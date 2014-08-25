(ns cmr.ingest.services.jobs
  "This contains the scheduled jobs for the ingest application."
  (:require [cmr.common.jobs :as jobs :refer [def-stateful-job defjob]]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.ingest.data.provider-acl-hash :as pah]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.common.log :refer (debug info warn error)]))

(def REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL
  "The number of seconds between jobs to check for ACL changes and reindex collections."
  3600)

(defn acls->provider-id-hashes
  "Converts acls to a map of provider-ids to hashes of the ACLs."
  [acls]
  (let [provider-id-to-acls (group-by (comp :provider-id :catalog-item-identity) acls)]
    (into {}
          (for [[provider-id provider-acls] provider-id-to-acls]
            ;; Convert them to a set so hash is consistent without order
            [provider-id (hash (set provider-acls))]))))

(defn reindex-collection-permitted-groups
  "Reindexes all collections in a provider if the acls have changed. This is necessary because
  the groups that have permission to find collections are indexed with the collections."
  [context]
  (let [providers (mdb/get-providers context)
        provider-id-acl-hashes (or (pah/get-provider-id-acl-hashes context) {})
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                         (echo-acls/get-acls-by-type context "CATALOG_ITEM"))]
    (doseq [provider-id providers]
      (let [current-hash (get current-provider-id-acl-hashes provider-id)
            saved-hash (get provider-id-acl-hashes provider-id)]
        (when (not= current-hash saved-hash)
          (info "Provider" provider-id "ACLs have changed. Reindexing collections")
          (indexer/reindex-provider-collections context provider-id))))
    (pah/save-provider-id-acl-hashes context current-provider-id-acl-hashes)))

;; Periodically checks the acls for a provider. When they change reindexes all the collections in a
;; provider.
(def-stateful-job ReindexCollectionPermittedGroups
  [ctx system]
  (let [context {:system system}]
    (reindex-collection-permitted-groups context)))

(def jobs
  "A list of jobs for ingest"
  [{:job-type ReindexCollectionPermittedGroups
    :interval REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL}])
