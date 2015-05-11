(ns cmr.acl.acl-fetcher
  "Provides functions to easily fetch ACLs from ECHO. It has the ability to use an acl cache if one
  is configured in the system. If the acl cache is used the job defined in this namespace should be
  used to keep the acls fresh. By using the cache and background job, ACLs will always be available
  for callers without any"
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.time-keeper :as tk]
            [cmr.common.jobs :refer [defjob]]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.cache :as cache]
            [clojurewerkz.quartzite.conversion :as qc]
            [clojure.set :as set]))

(def acl-cache-key
  "The key used to store the acl cache in the system cache map."
  :acls)

(defn create-acl-cache
  "Creates the acl cache using the given cmr cache protocol implementation and object-identity-types.
  The object-identity-types are specified and stored as extra information in the cache so that when
  fetching acls later we will always pull and retrieve all of the ACLs needed for the application.
  Otherwise we might pull a subset and put in the cache and it would look like to subsequent cache
  actions that acls with other object-identity-types didn't exist."
  [cache-impl object-identity-types]
  ;; Instead of creating a new map to hold this information that would have to implement the cache
  ;; protocol we just associate extra information on the cache impl.
  (assoc cache-impl :object-identity-types object-identity-types))

(defn- context->cached-object-identity-types
  "Gets the object identity types configured in the acl cache in the context."
  [context]
  (:object-identity-types (cache/context->cache context acl-cache-key)))

(defn refresh-acl-cache
  "Refreshes the acls stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching ACLs. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context acl-cache-key)
        updated-acls (echo-acls/get-acls-by-types
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
                                    (set (context->cached-object-identity-types context))))]
      (do
        (info (str "The application is not configured to cache acls of the following"
                   " object-identity-types so we will fetch them from ECHO each time they are needed. "
                   (pr-str not-cached-oits)))
        (echo-acls/get-acls-by-types context object-identity-types))
      ;; Fetch ACLs using a cache
      (->> (cache/get-value
             cache
             acl-cache-key
             #(echo-acls/get-acls-by-types
                context
                ;; All of the object identity types needed by the application are fetched. We want
                ;; the cache to contain all of the acls needed.
                (context->cached-object-identity-types context)))
           (filter (fn [acl]
                     (some #(get acl (echo-acls/acl-type->acl-key %))
                           object-identity-types)))))

    ;; No cache is configured. Directly fetch the acls.
    (echo-acls/get-acls-by-types context object-identity-types)))

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

