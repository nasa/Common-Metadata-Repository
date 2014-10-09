(ns cmr.acl.acl-cache
  "Maintains an in-memory local cache of ACLs that is refreshed via a background job. This keeps
  ACLs fresh available instantly for callers without any caller having to pay the price to fetch
  the acls."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.time-keeper :as tk]
            [cmr.common.jobs :refer [defjob]]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.cache :as cache]))

(def acl-cache-key
  "The key used to store the acl cache in the system cache map."
  :acls)

(defn create-acl-cache
  "Creates a new empty ACL cache."
  []
  (cache/create-cache))

(defn refresh-acl-cache
  "Refreshes the acls stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching ACLs. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [acl-cache (cache/context->cache context acl-cache-key)
        updated-acls (echo-acls/get-acls-by-type context "CATALOG_ITEM")]
    (cache/update-cache
      acl-cache
      #(assoc % :acls updated-acls))))

(defn get-acls
  "Gets the current cached acls."
  [context]
  (let [acl-cache (context->cache context acl-cache-key)]
    (cache/cache-lookup
      acl-cache
      :acls
      (fn []
        (echo-acls/get-acls-by-type context "CATALOG_ITEM")))))


(defjob RefreshAclCacheJob
  [ctx system]
  (refresh-acl-cache {:system system}))

(defn refresh-acl-cache-job
  [job-key]
  {:job-type RefreshAclCacheJob
   :job-key job-key
   :interval 3600})

