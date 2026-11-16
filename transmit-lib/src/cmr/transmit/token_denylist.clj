(ns cmr.transmit.token-denylist
  "Tokens and users blocked from CMR. Entries live in a Redis hash shared by all applications.
  Each instance checks requests against a snapshot of the hash that it reloads every
  token-denylist-refresh-seconds, keeping the last good snapshot when Redis is unavailable."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.log :refer [error info warn]]
   [cmr.common.util :as common-util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.tokens :as tokens])
  (:import
   (java.util.concurrent TimeUnit)
   (java.util.concurrent.locks ReentrantLock)
   (org.apache.commons.codec.digest DigestUtils)))

(def entry-types
  "Deny list entries block either one token, by its hash, or every token of a user."
  #{:token :user})

(def denylist-key
  "The Redis hash holding deny list entries."
  :token-denylist)

(def snapshot-cache-key
  "The cache key for this instance's snapshot of the deny list."
  :token-denylist-snapshot)

(declare token-denylist-refresh-seconds)
(defconfig token-denylist-refresh-seconds
  "How often each instance reloads the token deny list from Redis."
  {:default 30 :type Long})

(defn create-snapshot-cache
  "Creates the cache holding this instance's snapshot of the deny list."
  []
  (mem-cache/create-in-memory-cache))

(defn token-hash
  "Returns the SHA-256 hex digest identifying the token. For an EDL JWT only the signed header and
  payload are hashed, so every form of the token that passes signature verification hashes the
  same, however the signature is encoded."
  [token]
  (let [token (string/trim (tokens/bearer-stripped token))
        [header payload signature] (string/split token #"\." 3)
        signed (when (and signature
                          (re-matches #"[A-Za-z0-9=_-]+" header)
                          (re-matches #"[A-Za-z0-9=_-]+" payload))
                 (str header "." payload))
        ;; Classified by the signed part alone, whatever the signature holds. The anchored checks
        ;; above keep is-jwt-token?'s unanchored regex linear; it also throws on some bad headers.
        jwt? (and signed (try (common-util/is-jwt-token? (str signed ".x")) (catch Exception _ false)))]
    (DigestUtils/sha256Hex ^String (if jwt? signed token))))

(defn token-hash-for-log
  "The token's hash for log lines, or nil for no token or the system token."
  [token]
  (when (and token (not (transmit-config/echo-system-token? token)))
    (token-hash token)))

(defn- field
  [entry-type id]
  (str (name entry-type) ":" id))

(defn- parse-field
  [f]
  (let [[entry-type id] (string/split f #":" 2)
        entry-type (keyword entry-type)]
    (when (and (seq id) (entry-types entry-type))
      [entry-type id])))

(defn- store
  []
  ;; keys-to-track is left empty so /caches/clear-cache never wipes the list.
  (redis-hash-cache/create-redis-hash-cache
   {:read-connection (redis-config/redis-read-conn-opts)
    :primary-connection (redis-config/redis-conn-opts)}))

(defn- parse-metadata
  [v]
  (try
    (json/parse-string v true)
    (catch Exception _ {})))

(defn get-entries
  "Returns the deny list as {:token {hash metadata} :user {user-id metadata}}."
  []
  (reduce-kv (fn [entries f v]
               (if-let [[entry-type id] (parse-field f)]
                 (assoc-in entries [entry-type id] (parse-metadata v))
                 entries))
             {:token {} :user {}}
             (or (hash-cache/get-map (store) denylist-key) {})))

(defn add-entry!
  "Adds or replaces a deny list entry."
  [entry-type id metadata]
  (hash-cache/set-value (store) denylist-key (field entry-type id) (json/generate-string metadata)))

(defn remove-entry!
  "Removes a deny list entry. Returns true if it existed."
  [entry-type id]
  (pos? (or (hash-cache/remove-value (store) denylist-key (field entry-type id)) 0)))

(def ^:private empty-snapshot
  {:token #{} :user #{}})

(defn- load-snapshot
  []
  (reduce (fn [snapshot f]
            (if-let [[entry-type id] (parse-field f)]
              (update snapshot entry-type conj (if (= :user entry-type) (string/lower-case id) id))
              snapshot))
          empty-snapshot
          (hash-cache/get-keys (store) denylist-key)))

(defn- now-ms
  []
  ;; Monotonic, so wall clock changes can't stop refreshes.
  (quot (System/nanoTime) 1000000))

(defn- fresh?
  [{:keys [expires-at]}]
  (and expires-at (< (now-ms) expires-at)))

(defonce ^:private ^ReentrantLock refresh-lock (ReentrantLock.))

;; Survives /caches/clear-cache so a reset during a Redis outage doesn't drop the list.
(defonce ^:private last-loaded (atom nil))

(def ^:private first-load-wait-ms
  "How long a request waits for an instance's first load of the list before going unchecked."
  500)

(defn- entry-count
  [snapshot]
  (+ (count (:token snapshot)) (count (:user snapshot))))

(defn- log-change
  [previous loaded]
  (when (not= previous loaded)
    (let [msg (format "Token deny list now has %d token and %d user entries, previously %d."
                      (count (:token loaded)) (count (:user loaded)) (entry-count previous))]
      (if (< (entry-count loaded) (entry-count previous))
        (warn msg)
        (info msg)))))

(defn- refresh!
  [snapshot-cache]
  (let [cached (cache/get-value snapshot-cache :snapshot)]
    (if (fresh? cached)
      (:entries cached)
      (let [previous (or (:entries cached) @last-loaded)
            entries (try
                      (let [loaded (load-snapshot)]
                        (log-change previous loaded)
                        (reset! last-loaded loaded))
                      (catch Exception e
                        (error (if @last-loaded
                                 "Unable to reload the token deny list, keeping the previous copy:"
                                 "Unable to load the token deny list, allowing all tokens until it loads:")
                               (.getMessage e))
                        (or previous empty-snapshot)))]
        (cache/set-value snapshot-cache :snapshot
                         {:entries entries
                          :expires-at (+ (now-ms) (* 1000 (token-denylist-refresh-seconds)))})
        entries))))

(defn- current-snapshot
  "Returns this instance's snapshot, reloading it from Redis once it is older than the refresh
  interval. While one request reloads, others keep using the previous snapshot. Before the first
  load, requests wait up to first-load-wait-ms for it. A failed reload keeps the previous
  snapshot."
  [snapshot-cache]
  (let [cached (cache/get-value snapshot-cache :snapshot)
        previous (or (:entries cached) @last-loaded)]
    (cond
      (fresh? cached) (:entries cached)

      previous (if (.tryLock refresh-lock)
                 (try
                   (refresh! snapshot-cache)
                   (finally (.unlock refresh-lock)))
                 previous)

      (.tryLock refresh-lock first-load-wait-ms TimeUnit/MILLISECONDS)
      (try
        (refresh! snapshot-cache)
        (finally (.unlock refresh-lock)))

      :else empty-snapshot)))

(defn reload-snapshot
  "Makes the next request on this instance read the deny list from Redis."
  [context]
  (when-let [snapshot-cache (cache/context->cache context snapshot-cache-key)]
    (when-let [cached (cache/get-value snapshot-cache :snapshot)]
      (cache/set-value snapshot-cache :snapshot (assoc cached :expires-at nil)))))

(defn user-denied?
  "Returns true if the user id is on the deny list."
  [context user-id]
  (boolean
   (when-let [snapshot-cache (cache/context->cache context snapshot-cache-key)]
     (contains? (:user (current-snapshot snapshot-cache)) (some-> user-id string/lower-case)))))

(defn denied?
  "Returns true if the token, or the user it belongs to, is on the deny list. user-id-fn is
  only called when users are listed, and if it throws the exception propagates so the request
  fails rather than going unchecked. User ids match case-insensitively."
  [context token user-id-fn]
  (when-let [snapshot-cache (cache/context->cache context snapshot-cache-key)]
    (let [{tokens :token users :user} (current-snapshot snapshot-cache)]
      (boolean
       (or (and (seq tokens)
                (contains? tokens (token-hash token)))
           (and (seq users)
                (contains? users (some-> (user-id-fn) string/lower-case))))))))
