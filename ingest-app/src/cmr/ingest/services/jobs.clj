(ns cmr.ingest.services.jobs
  "This contains the scheduled jobs for the ingest application."
  (:require
    [clj-time.core :as t]
    [clojure.string :as string]
    [cmr.acl.acl-fetcher :as acl-fetcher]
    [cmr.common.config :as cfg :refer [defconfig]]
    [cmr.common.jobs :as jobs :refer [def-stateful-job defjob]]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.ingest.data.bulk-update :as bulk-update]
    [cmr.ingest.data.ingest-events :as ingest-events]
    [cmr.ingest.data.provider-acl-hash :as pah]
    [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]
    [cmr.transmit.echo.acls :as echo-acls]
    [cmr.transmit.metadata-db :as mdb]
    [cmr.transmit.search :as search]))

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
        (let [resp (mdb/save-concept context {:concept-id concept-id :deleted true})]
          (ingest-events/publish-ingest-event
           context (ingest-events/concept-expire-event resp)))))))

(defn- create-query-params
  "Create query parameters using the query string like
  \"polygon=1,2,3&concept-id=G1-PROV1\""
  [query-string]
  (let [query-string-list (string/split query-string #"&")
        query-map-list (map #(let [a (string/split % #"=")]
                               {(first a) (second a)})
                             query-string-list)]
     (apply merge query-map-list)))

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

(defconfig email-subscription-processing-interval
  "Number of seconds between jobs processing email subscriptions."
  {:default 3600
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

(defn- process-subscriptions
  "Process each subscription in subscriptions."
  [context subscriptions time-constraint]
  (for [subscription subscriptions
       :let [email-address (:email-address subscription)
             coll-id (:collection-concept-id subscription)
             query-params (create-query-params (:metadata subscription))
             params1 (merge {"created-at" time-constraint}
                            {"collection-concept-id" coll-id}
                            query-params)
             params2 (merge {"revision-date" time-constraint}
                            {"collection-concept-id" coll-id}
                            query-params)
             gran-ref1 (search/find-granule-references context params1)
             gran-ref2 (search/find-granule-references context params2)
             gran-ref (distinct (concat gran-ref1 gran-ref2))]]
      (if (seq gran-ref)
        ;; These strings are just for debugging purpose. In another ticket
        ;; I will figure out how to send email.
        (str "Granules found for: " email-address)
        (str "No granules found for: " email-address))))

(defn- email-subscription-processing
  "Process email subscriptions and send email when found granules matching the collection and queries
  in the subscription and were created/updated during the last processing interval."
  [context]
  (let [end-time (t/now)
        start-time (t/minus end-time (t/seconds (email-subscription-processing-interval)))
        time-constraint (str (str start-time) "," (str end-time))
        subscriptions
         (->> (mdb/find-concepts context {:latest true} :subscription)
              (filter #(not (:deleted %)))
              (map #(select-keys % [:email-address :metadata :collection-concept-id])))]
    ;; for some reason, the info or println doesn't show inside the for loop in my local
    ;; proto repl.
    (println (process-subscriptions context subscriptions time-constraint))))

(def-stateful-job BulkUpdateStatusTableCleanup
  [_ system]
  (bulk-update-status-table-cleanup {:system system}))

(def-stateful-job EmailSubscriptionProcessing
  [_ system]
  (email-subscription-processing {:system system}))

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
    :interval (email-subscription-processing-interval)}

   {:job-type TriggerPartialRefreshCollectionGranuleAggregationCacheJob
    :interval (partial-refresh-collection-granule-aggregation-cache-interval)}

   {:job-type TriggerFullRefreshCollectionGranuleAggregationCacheJob
    ;; Everyday at 11:20 am so it's before the reindex all collections job
    :daily-at-hour-and-minute [11 20]}

   {:job-type ReindexAllCollections
    ;; Run everyday at 12:20 pm. Chosen because it's least busy time for indexer historically and also
    ;; during business hours when people can debug issues. It's offset from the top of the hour so as
    ;; not to be at the same time as EDSC fetches all the collection metadata.
    :daily-at-hour-and-minute [12 20]}])
