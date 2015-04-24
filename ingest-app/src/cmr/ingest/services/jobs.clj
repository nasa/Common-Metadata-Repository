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

(def CLEANUP_EXPIRED_COLLECTIONS_INTERVAL
  "The number of seconds between jobs to cleanup expired collections"
  3600)

(defn acls->provider-id-hashes
  "Converts acls to a map of provider-ids to hashes of the ACLs."
  [acls]
  (let [provider-id-to-acls (group-by (comp :provider-id :catalog-item-identity) acls)]
    (into {}
          (for [[provider-id provider-acls] provider-id-to-acls]
            ;; Convert them to a set so hash is consistent without order
            [provider-id (hash (set provider-acls))]))))

(defn reindex-all-collections
  "Reindexes all collections in all providers"
  [context]
  (let [providers (mdb/get-providers context)
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                         (echo-acls/get-acls-by-types context [:catalog-item]))]
    (info "Reindexing collections in all providers:" (pr-str providers))
    (indexer/reindex-provider-collections context providers)

    (pah/save-provider-id-acl-hashes context current-provider-id-acl-hashes)))

(defn reindex-collection-permitted-groups
  "Reindexes all collections in a provider if the acls have changed. This is necessary because
  the groups that have permission to find collections are indexed with the collections."
  [context]
  (let [providers (mdb/get-providers context)
        provider-id-acl-hashes (or (pah/get-provider-id-acl-hashes context) {})
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                         (echo-acls/get-acls-by-types context [:catalog-item]))
        providers-requiring-reindex (filter (fn [provider-id]
                                              (not= (get current-provider-id-acl-hashes provider-id)
                                                    (get provider-id-acl-hashes provider-id)))
                                            providers)]
    (when (seq providers-requiring-reindex)
      (info "Providers" (pr-str providers-requiring-reindex)
            "ACLs have changed. Reindexing collections")
      (indexer/reindex-provider-collections context providers-requiring-reindex))

    (pah/save-provider-id-acl-hashes context current-provider-id-acl-hashes)))


;; Periodically checks the acls for a provider. When they change reindexes all the collections in a
;; provider.
(def-stateful-job ReindexCollectionPermittedGroups
  [ctx system]
  (let [context {:system system}]
    (reindex-collection-permitted-groups context)))

;; Reindexes all collections for providers regardless of whether the ACLs have changed or not.
;; This is done as a temporary fix for CMR-1311 but we may keep it around to help temper other race
;; conditions that may occur.
(def-stateful-job ReindexAllCollections
  [ctx system]
  (let [context {:system system}]
    (reindex-all-collections context)))

(defn cleanup-expired-collections
  "Finds collections that have expired (have a delete date in the past) and removes them from
  metadata db and the index"
  [context]
  (doseq [provider-id (mdb/get-providers context)]
    (info "Cleaning up expired collections for provider" provider-id)
    (when-let [concept-ids (mdb/get-expired-collection-concept-ids context provider-id)]
      (info "Removing expired collections:" (pr-str concept-ids))
      (doseq [concept-id concept-ids]
        (let [revision-id (mdb/delete-concept context concept-id)]
          (indexer/delete-concept-from-index context concept-id revision-id))))))

(def-stateful-job CleanupExpiredCollections
  [ctx system]
  (let [context {:system system}]
    (cleanup-expired-collections context)))

(def jobs
  "A list of jobs for ingest"
  [{:job-type ReindexCollectionPermittedGroups
    :interval REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL}
   {:job-type ReindexAllCollections
    ;; Run everyday at 2 am. Chosen because it is offset from the bootstrap database synchronize job by 2 hours.
    :daily-at-hour-and-minute [2 0]}
   {:job-type CleanupExpiredCollections
    :interval CLEANUP_EXPIRED_COLLECTIONS_INTERVAL}])
