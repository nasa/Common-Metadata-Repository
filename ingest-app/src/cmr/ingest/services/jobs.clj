(ns cmr.ingest.services.jobs
  "This contains the scheduled jobs for the ingest application."
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.spec.alpha :as spec]
   [clojure.string :as string]
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs :refer [def-stateful-job]]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.ingest.data.bulk-update :as bulk-update]
   [cmr.ingest.data.granule-bulk-update :as granule-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.data.provider-acl-hash :as pah]
   [cmr.ingest.services.granule-bulk-update-service :as gran-bulk-update-svc]
   [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]
   [cmr.ingest.services.subscriptions-helper :as subscription]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db :as mdb]
   [postal.core :as postal-core]))

;; Specs =============================================================
(def date-rx "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")

(def time-constraint-pattern (re-pattern (str date-rx "," date-rx)))

(spec/def ::time-constraint (spec/and
                              string?
                              #(re-matches time-constraint-pattern %)))

;; Call the following to trigger a job, example below will fire an email subscription
;; UPDATE QRTZ_TRIGGERS
;; SET NEXT_FIRE_TIME =(((cast (SYS_EXTRACT_UTC(SYSTIMESTAMP) as DATE) - DATE'1970-01-01')*86400 + 1200) * 1000)
;; WHERE trigger_name='EmailSubscriptionProcessing.job.trigger';

(def REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL
  "The number of seconds between jobs to check for ACL changes and reindex collections."
  3600)

(def CLEANUP_EXPIRED_COLLECTIONS_INTERVAL
  "The number of seconds between jobs to cleanup expired collections"
  3600)

(def EMAIL_SUBSCRIPTION_PROCESSING_JOB_KEY
  "Quartz job key for EmailSubscriptionProcessing job."
  "EmailSubscriptionProcessing.job")

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
  [context force-version?]

  ;; Refresh the acls
  ;; This is done because we want to make sure we have the latest acls cached. This will update
  ;; the hash code stored in the consistent cache. Both ingest and the indexer use the consistent
  ;; cache for acls so they are kept in sync. This removes the need for the indexer to refresh
  ;; the cache on each message that it processes.
  (acl-fetcher/refresh-acl-cache context)

  (let [providers (map :provider-id (mdb/get-providers context))
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                         (acl-fetcher/get-acls context [:catalog-item]))]
    (info "Sending events to reindex collections in all providers:" (pr-str providers))
    (doseq [provider providers]
      (ingest-events/publish-provider-event
        context
        (ingest-events/provider-collections-require-reindexing-event provider force-version?)))

    (info "Reindexing all collection events submitted. Saving provider acl hashes")
    (pah/save-provider-id-acl-hashes context current-provider-id-acl-hashes)
    (info "Saving provider acl hashes complete")))

(defn reindex-collection-permitted-groups
  "Reindexes all collections in a provider if the acls have changed. This is necessary because
  the groups that have permission to find collections are indexed with the collections."
  [context]

  ;; Refresh the acls
  ;; This is done because we want to make sure we have the latest acls cached. This will update
  ;; the hash code stored in the consistent cache. Both ingest and the indexer use the consistent
  ;; cache for acls so they are kept in sync. This removes the need for the indexer to refresh
  ;; the cache on each message that it processes.
  (acl-fetcher/refresh-acl-cache context)

  (let [providers (map :provider-id (mdb/get-providers context))
        provider-id-acl-hashes (or (pah/get-provider-id-acl-hashes context) {})
        current-provider-id-acl-hashes (acls->provider-id-hashes
                                        (acl-fetcher/get-acls context [:catalog-item]))
        providers-requiring-reindex (filter (fn [provider-id]
                                              (not= (get current-provider-id-acl-hashes provider-id)
                                                    (get provider-id-acl-hashes provider-id)))
                                            providers)]
    (when (seq providers-requiring-reindex)
      (info "Providers" (pr-str providers-requiring-reindex)
            "ACLs have changed. Reindexing collections")
      (doseq [provider providers-requiring-reindex]
        (ingest-events/publish-provider-event
          context
          (ingest-events/provider-collections-require-reindexing-event provider false))))

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
    (reindex-all-collections context false)))

(defn cleanup-expired-collections
  "Finds collections that have expired (have a delete date in the past) and removes them from
  metadata db and the index"
  [context]
  (doseq [{:keys [provider-id]} (mdb/get-providers context)]
    (info "Cleaning up expired collections for provider" provider-id)
    (when-let [concept-ids (mdb/get-expired-collection-concept-ids context provider-id)]
      (info "Removing expired collections:" (pr-str concept-ids))
      (doseq [concept-id concept-ids]
       (mdb/save-concept context {:concept-id concept-id :deleted true})))))

(def-stateful-job CleanupExpiredCollections
  [ctx system]
  (let [context {:system system}]
    (cleanup-expired-collections context)))

(def-stateful-job RefreshHumanizerAliasCache
  [_ system]
  (let [context {:system system}]
    (humanizer-alias-cache/refresh-cache context)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Jobs for refreshing the collection granule aggregation cache in the indexer. This is a singleton job
;; and the indexer does not have a database so it's triggered from Ingest and sent via message.
;; Only one node needs to refresh the cache because we're using the  fallback cache with Redis cache.
;; The value stored in Redis will be available to all the nodes.

(defconfig partial-refresh-collection-granule-aggregation-cache-interval
  "Number of seconds between partial refreshes of the collection granule aggregation cache."
  {:default 3600
   :type Long})

(defconfig refresh-humanizer-alias-cache-interval
  "Number of seconds between refreshes of the humanizer alias cache."
  {:default 3600
   :type Long})

(defconfig bulk-update-status-table-cleanup-interval
  "Number of seconds between cleanup of the old status rows."
  {:default 86400 ;;24 hours
   :type Long})

(defconfig bulk-granule-task-table-cleanup-interval
  "Number of seconds between runs of cleanup job"
  {:default 86400 ;;24 hours
   :type Long})

(defconfig bulk-update-task-status-update-poll-interval
  "Number of seconds between runs bulk granule task status update jobs."
  {:default 300 ;; 5 minutes
   :type Long})

(defn trigger-full-refresh-collection-granule-aggregation-cache
  "Triggers a refresh of the collection granule aggregation cache in the Indexer."
  [context]
  (ingest-events/publish-provider-event
   context
   (ingest-events/trigger-collection-granule-aggregation-cache-refresh nil)))

(defn trigger-partial-refresh-collection-granule-aggregation-cache
  "Triggers a partial refresh of the collection granule aggregation cache in the Indexer."
  [context]
  (ingest-events/publish-provider-event
    context
    (ingest-events/trigger-collection-granule-aggregation-cache-refresh
     ;; include a 5 minute buffer
     (+ 300 (partial-refresh-collection-granule-aggregation-cache-interval)))))

;; Refreshes collections updated in past interval time period.
(def-stateful-job TriggerPartialRefreshCollectionGranuleAggregationCacheJob
  [_ system]
  (trigger-partial-refresh-collection-granule-aggregation-cache
   {:system system}))

;; Fully refreshes the cache
(def-stateful-job TriggerFullRefreshCollectionGranuleAggregationCacheJob
  [_ system]
  (trigger-full-refresh-collection-granule-aggregation-cache
   {:system system}))

(defn bulk-update-status-table-cleanup
  "Clean up the rows in the bulk-update-task-status table that are older than the configured age"
  [context]
  (bulk-update/cleanup-old-bulk-update-status context))

(defn trigger-bulk-granule-update-task-table-cleanup
  "Trigger cleanup of completed bulk granule update tasks that are older than the configured age"
  [context]
  (ingest-events/publish-gran-bulk-update-event
   context
   (ingest-events/granule-bulk-update-task-cleanup-event)))

(defn trigger-autocomplete-suggestions-reindex
  "Triggers an autocomplete reindex for all providers followed by a prune to remove stale suggestions."
  [context]
  (let [providers (map :provider-id (mdb/get-providers context))]
    (info "Sending events to reindex autocomplete suggestions in providers:" (pr-str providers))
    (doseq [provider providers]
      (ingest-events/publish-provider-event
       context
       (ingest-events/provider-autocomplete-suggestion-reindexing-event provider)))
    (info "Sending event to prune autocomplete suggestions.")
    (ingest-events/publish-provider-event
     context
     (ingest-events/autocomplete-suggestion-prune-event))))

(def-stateful-job BulkUpdateStatusTableCleanup
  [_ system]
  (bulk-update-status-table-cleanup {:system system}))

(def-stateful-job BulkGranUpdateTaskCleanup
  [_ system]
  (granule-bulk-update/cleanup-bulk-granule-tasks {:system system}))

(def-stateful-job EmailSubscriptionProcessing
  [_ system]
  (subscription/email-subscription-processing {:system system}))

(def-stateful-job ReindexAutocompleteSuggestions
  [_ system]
  (trigger-autocomplete-suggestions-reindex {:system system}))

(def-stateful-job BulkGranTaskStatusUpdatePoll
  [_ system]
  (gran-bulk-update-svc/update-completed-task-status! {:system system}))

(defn jobs
  "A list of jobs for ingest"
  []
  [{:job-type ReindexCollectionPermittedGroups
    :interval REINDEX_COLLECTION_PERMITTED_GROUPS_INTERVAL}

   {:job-type CleanupExpiredCollections
    :interval CLEANUP_EXPIRED_COLLECTIONS_INTERVAL}

   {:job-type RefreshHumanizerAliasCache
    :interval (refresh-humanizer-alias-cache-interval)}

   {:job-type BulkUpdateStatusTableCleanup
    :interval (bulk-update-status-table-cleanup-interval)}

   {:job-type EmailSubscriptionProcessing
    :job-key EMAIL_SUBSCRIPTION_PROCESSING_JOB_KEY
    :interval (subscription/email-subscription-processing-interval)}

   {:job-type TriggerPartialRefreshCollectionGranuleAggregationCacheJob
    :interval (partial-refresh-collection-granule-aggregation-cache-interval)}

   {:job-type TriggerFullRefreshCollectionGranuleAggregationCacheJob
    ;; Everyday at 11:20 am so it's before the reindex all collections job
    :daily-at-hour-and-minute [11 20]}

   {:job-type ReindexAllCollections
    ;; Run everyday at 12:20 pm. Chosen because it's least busy time for indexer historically and also
    ;; during business hours when people can debug issues. It's offset from the top of the hour so as
    ;; not to be at the same time as EDSC fetches all the collection metadata.
    :daily-at-hour-and-minute [12 20]}

   {:job-type ReindexAutocompleteSuggestions
    ;; Run everyday at 13:20. Chosen to be offset from the last job
    :daily-at-hour-and-minute [13 20]}

   {:job-type BulkGranUpdateTaskCleanup
    :interval (bulk-granule-task-table-cleanup-interval)}

   {:job-type BulkGranTaskStatusUpdatePoll
    :interval (bulk-update-task-status-update-poll-interval)}])
