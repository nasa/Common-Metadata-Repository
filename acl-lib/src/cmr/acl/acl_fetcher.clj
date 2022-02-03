(ns cmr.acl.acl-fetcher
  "Provides functions to easily fetch ACLs from ECHO. It has the ability to use an acl cache if one
  is configured in the system. If the acl cache is used the job defined in this namespace should be
  used to keep the acls fresh. By using the cache and background job, ACLs will always be available
  for callers without any"
  (:require
   [clojure.set :as set]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]
   [cmr.common.cache :as cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as config]))

(def acl-cache-key
  "The key used to store the acl cache in the system cache map."
  :acls)

(def acl-keys-to-track
  "The collection of keys which should be deleted from redis whenever someone attempts to clear the
  ACL cache."
  [":acls-hash-code"])

(defn create-acl-cache*
  "Creates the acl cache using the given cmr cache protocol implementation and object-identity-types.
  The object-identity-types are specified and stored as extra information in the cache so that when
  fetching acls later we will always pull and retrieve all of the ACLs needed for the application.
  Otherwise we might pull a subset and put in the cache and it would look like to subsequent cache
  actions that acls with other object-identity-types didn't exist."
  [cache-impl object-identity-types]
  ;; Instead of creating a new map to hold this information that would have to implement the cache
  ;; protocol we just associate extra information on the cache impl.
  (assoc cache-impl :object-identity-types object-identity-types))

(defn create-acl-cache
  "Creates the acl cache using the given object-identity-types."
  [object-identity-types]
  (create-acl-cache* (stl-cache/create-single-thread-lookup-cache) object-identity-types))

(defconfig acl-cache-consistent-timeout-seconds
  "The number of seconds between when the ACL cache should check with redis for consistence"
  {:default 30
   :type Long})

(defn create-consistent-acl-cache
  "Creates the acl cache using the given object-identity-types that uses redis for consistency."
  [object-identity-types]
  (create-acl-cache* (stl-cache/create-single-thread-lookup-cache
                      (consistent-cache/create-consistent-cache
                       {:hash-timeout-seconds (acl-cache-consistent-timeout-seconds)
                        :keys-to-track acl-keys-to-track}))
                     object-identity-types))

(def identity-string-map
  "Maps old ECHO identity to cmr identity type string."
  {:system-object "system"
   :provider-object "provider"
   :single-instance-object "single_instance"
   :catalog-item "catalog_item"})

(defn- object-identity-types->identity-strings
  "Converts object identity types to the identity strings expected from access control"
  [object-identity-types]
  (map identity-string-map object-identity-types))

(defn- context->cached-object-identity-types
  "Gets the object identity types configured in the acl cache in the context."
  [context]
  (:object-identity-types (cache/context->cache context acl-cache-key)))

(defn- get-all-acls
  "Calls acl search endpoint using object-identity-types. Pages through results as needed."
  [context object-identity-types]
  (let [page-size 2000
        response (access-control/search-for-acls (assoc context :token (config/echo-system-token))
                                                 {:identity-type (object-identity-types->identity-strings
                                                                  object-identity-types)
                                                  :include-full-acl true
                                                  :page-size page-size})
        total-pages (int (Math/ceil (/ (get response :hits 0) page-size)))]
    (if (> total-pages 1)
      ;; Take the items from first page of the response from above,
      ;; and concat each page after that in sequence.
      (reduce conj
              [response]
              (for [page-num (range 2 (inc total-pages))
                    :let [response (access-control/search-for-acls (assoc context :token (config/echo-system-token))
                                                                   {:identity-type (object-identity-types->identity-strings
                                                                                    object-identity-types)
                                                                    :include-full-acl true
                                                                    :page-size page-size
                                                                    :page-num page-num})]]
                response))
      [response])))

(defn- process-search-for-acls
  "Processes response and formats it for get-all-acls"
  [context object-identity-types]
  (->> (get-all-acls context object-identity-types)
       (mapcat :items)
       (map :acl)
       (map util/map-keys->kebab-case)))

(defn expire-consistent-cache-hashes
  "Forces the cached hash codes of an ACL consistent cache to expire so that subsequent requests for
   ACLs will check redis for consistency."
  [context]
  (let [cache (cache/context->cache context acl-cache-key)]
    (consistent-cache/expire-hash-cache-timeouts (:delegate-cache cache))))

(defn refresh-acl-cache
  "Refreshes the acls stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching ACLs. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context acl-cache-key)
        updated-acls (process-search-for-acls
                       ;; All of the object identity types needed by the application are fetched. We want
                       ;; the cache to contain all of the acls needed.
                       context (context->cached-object-identity-types context))]
    (cache/set-value cache acl-cache-key updated-acls)))

(defn get-acls
  "Gets the current acls limited to a specific set of object identity types."
  [context object-identity-types]
  (if-let [cache (cache/context->cache context acl-cache-key)]
    ;; Check that we're caching the requested object identity types
    ;; Otherwise we'd just silently fail to find any acls.
    (if-let [not-cached-oits (seq (set/difference
                                    (set object-identity-types)
                                    (set (context->cached-object-identity-types
                                           context))))]
      (do
        (info (str "The application is not configured to cache acls of the "
                   "following object-identity-types so we will fetch them "
                   "from access-control each time they are needed. "
                   (pr-str not-cached-oits)))
        (process-search-for-acls context object-identity-types))
      ;; Fetch ACLs using a cache
      (filter
        (fn [acl]
          (some #(get acl (access-control/acl-type->acl-key %))
                object-identity-types))
        (cache/get-value
          cache
          acl-cache-key
          #(process-search-for-acls
            context
            ;; All of the object identity types needed by the application are
            ;; fetched. We want the cache to contain all of the acls needed.
            (context->cached-object-identity-types context)))))

    ;; No cache is configured. Directly fetch the acls.
    (process-search-for-acls context object-identity-types)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing ACLs in the cache.

(defjob RefreshAclCacheJob
  [ctx system]
  (refresh-acl-cache {:system system}))

(defn refresh-acl-cache-job
  [job-key]
  {:job-type RefreshAclCacheJob
   :job-key job-key
   :interval 3600})

(comment
 (do
   (def context (cmr.access-control.test.util/conn-context))
   (process-search-for-acls (assoc context :token (config/echo-system-token)) [:catalog-item])
   (cmr.transmit.echo.acls/get-acls-by-types context [:catalog-item])
   (get-acls context [:catalog-item])))
