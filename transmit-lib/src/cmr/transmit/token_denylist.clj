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
   [cmr.common.log :refer [error]]
   [cmr.common.time-keeper :as tk]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache])
  (:import
   (java.util Base64)
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

(defn- canonical-jwt
  "Re-encodes each segment of a JWT without padding, or returns nil if token isn't one. JWT
  verification accepts several encodings of the same signature, so the hash has to use one."
  [token]
  (let [segments (string/split token #"\." -1)]
    (when (= 3 (count segments))
      (try
        (let [^java.util.Base64$Decoder decoder (Base64/getUrlDecoder)
              ^java.util.Base64$Encoder encoder (.withoutPadding (Base64/getUrlEncoder))]
          (string/join "." (map #(.encodeToString encoder (.decode decoder ^String %)) segments)))
        (catch IllegalArgumentException _ nil)))))

(defn token-hash
  "Returns the SHA-256 hex digest of the token, ignoring any Bearer prefix and normalizing the
  encoding of JWTs."
  [token]
  (let [token (string/replace (string/trim token) #"^Bearer\s+" "")]
    (DigestUtils/sha256Hex ^String (or (canonical-jwt token) token))))

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
              (update snapshot entry-type conj id)
              snapshot))
          empty-snapshot
          (hash-cache/get-keys (store) denylist-key)))

(defn- fresh?
  [{:keys [expires-at]}]
  (and expires-at (< (tk/now-ms) expires-at)))

(defonce ^:private ^ReentrantLock refresh-lock (ReentrantLock.))

;; Survives /caches/clear-cache so a reset during a Redis outage doesn't drop the list.
(defonce ^:private last-loaded (atom nil))

(defn- refresh!
  [snapshot-cache]
  (let [cached (cache/get-value snapshot-cache :snapshot)]
    (if (fresh? cached)
      (:entries cached)
      (let [previous (or (:entries cached) @last-loaded)
            entries (try
                      (reset! last-loaded (load-snapshot))
                      (catch Exception e
                        (error (if previous
                                 "Unable to reload the token deny list, keeping the previous copy:"
                                 "Unable to load the token deny list, allowing all tokens until it loads:")
                               (.getMessage e))
                        (or previous empty-snapshot)))]
        (cache/set-value snapshot-cache :snapshot
                         {:entries entries
                          :expires-at (+ (tk/now-ms) (* 1000 (token-denylist-refresh-seconds)))})
        entries))))

(defn- current-snapshot
  "Returns this instance's snapshot, reloading it from Redis once it is older than the refresh
  interval. While one request reloads, others keep using the previous snapshot; they only wait
  when there is none yet. A failed reload keeps the previous snapshot."
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

      :else (do
              (.lock refresh-lock)
              (try
                (refresh! snapshot-cache)
                (finally (.unlock refresh-lock)))))))

(defn reload-snapshot
  "Makes the next request on this instance read the deny list from Redis."
  [context]
  (when-let [snapshot-cache (cache/context->cache context snapshot-cache-key)]
    (when-let [cached (cache/get-value snapshot-cache :snapshot)]
      (cache/set-value snapshot-cache :snapshot (assoc cached :expires-at 0)))))

(defn denied?
  "Returns true if the token, or the user it belongs to, is on the deny list. user-id-fn is
  only called when users are listed; if it throws, the user is treated as not listed."
  [context token user-id-fn]
  (when-let [snapshot-cache (cache/context->cache context snapshot-cache-key)]
    (let [{tokens :token users :user} (current-snapshot snapshot-cache)]
      (boolean
       (or (and (seq tokens)
                (contains? tokens (token-hash token)))
           (and (seq users)
                (contains? users (try (user-id-fn) (catch Exception _ nil)))))))))
