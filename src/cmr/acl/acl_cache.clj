(ns cmr.acl.acl-cache
  "Maintains an in-memory local cache of ACLs that is refreshed via a background job. This keeps
  ACLs fresh available instantly for callers without any caller having to pay the price to fetch
  the acls."
  (:require [cmr.common.services.errors :as errors]
            [clj-time.core :as t]
            [cmr.common.jobs :refer [defjob]]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.transmit.echo.providers :as echo-providers]
            [cmr.common.log :as log :refer (debug info warn error)]
            [clojure.walk :as w]))

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
  (get-in context [:system :acl-cache]))

(defn- set-acl-provider-id
  "Sets the provider-id in the acl to replace the provider guid"
  [provider-guid-id-map acl]
  (update-in acl
             [:catalog-item-identity]
             (fn [cii]
               (-> cii
                   (assoc :provider-id (provider-guid-id-map (:provider-guid cii)))
                   (dissoc :provider-guid)))))

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
  (let [acl-cache-atom (context->acl-cache context)
        provider-guid-id-map (echo-providers/get-provider-guid-id-map context)
        acls (mapv (partial set-acl-provider-id provider-guid-id-map)
                   (echo-acls/get-acls-by-type context "CATALOG_ITEM"))]
    (reset! acl-cache-atom {:acls acls :last-updated (t/now)})))

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

;; TODO we need a way to manually trigger this from integration tests.
;; Add an endpoint to search and indexer for refreshing the acl cache.
;; We could make it part of the refresh call that the cache is cleared and then allow a search hitting
;; a null cache to trigger it being refreshed.

(defjob RefreshAclCacheJob
  [ctx system]
  (refresh-acl-cache {:system system}))

(defn refresh-acl-cache-job
  [job-key]
  {:job-type RefreshAclCacheJob
   :job-key job-key
   :interval 3600})

