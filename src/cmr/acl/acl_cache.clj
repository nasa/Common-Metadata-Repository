(ns cmr.acl.acl-cache
  "Maintains an in-memory local cache of ACLs that is refreshed via a background job. This keeps
  ACLs fresh available instantly for callers without any caller having to pay the price to fetch
  the acls."
  (:require [cmr.common.services.errors :as errors]
            [clj-time.core :as t]
            [cmr.common.jobs :refer [defjob]]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.common.log :as log :refer (debug info warn error)]))

(def initial-cache-state
  {:acls nil
   :last-updated nil})

(defn create-acl-cache
  "Creates a new empty ACL cache. The cache itself is just an atom with a map."
  []
  (atom initial-cache-state))

(defn- context->acl-cache
  "Gets the acl cache from the context"
  [context]
  (get-in context [:system :caches :acls]))

(defn reset
  "Resets the cache back to it's initial state"
  [context]
  (-> context
      context->acl-cache
      (reset! initial-cache-state)))

(defn refresh-acl-cache
  "Refreshes the acls stored in the cache. This should be called from a background job on a timer
  to keep the cache fresh. This will throw an exception if there is a problem fetching ACLs. The
  caller is responsible for catching and logging the exception."
  [context]
  (let [acl-cache-atom (context->acl-cache context)]
    (reset! acl-cache-atom {:acls (echo-acls/get-acls-by-type context "CATALOG_ITEM")
                            :last-updated (t/now)})))

(defn get-acls
  "Gets the current cached acls."
  [context]
  (let [acl-cache-atom (context->acl-cache context)]
    (when-not (-> acl-cache-atom deref :acls)
      (info "No acls found in cache. Manually triggering acl cache refresh")
      (refresh-acl-cache context))
    (if-let [acls (-> context context->acl-cache deref :acls)]
      acls
      (errors/internal-error! "ACLS were not in cache."))))

(defjob RefreshAclCacheJob
  [ctx system]
  (refresh-acl-cache {:system system}))

(defn refresh-acl-cache-job
  [job-key]
  {:job-type RefreshAclCacheJob
   :job-key job-key
   :interval 3600})

