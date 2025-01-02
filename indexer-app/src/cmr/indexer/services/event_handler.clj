(ns cmr.indexer.services.event-handler
  "Provides functions for subscribing to and handling events."
  (:require
   [cmr.common.concepts :as cc]
   [cmr.common.log :refer [debug]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.collection-granule-aggregation-cache :as cgac]
   [cmr.indexer.data.concepts.deleted-granule :as deleted-granule]
   [cmr.indexer.services.autocomplete :as autocomplete]
   [cmr.indexer.services.index-service :as indexer]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]))

;; Isolating provider events from other ingest events to prevent them from ever being processed
;; by the normal ingest handlers as that can lead to a swamped ingest queue. These handlers
;; are reserved for the provider queue which is configured to handle long processing times.
(defmulti handle-provider-event
  "Handle the various actions that can be requested for a provider via the provider-queue"
  (fn [_context msg]
    (keyword (:action msg))))

(defmethod handle-provider-event :provider-collection-reindexing
  [context {:keys [provider-id force-version? all-revisions-index?]}]
  ;; We set the refresh acls flag to false because the ACLs should have been refreshed as part
  ;; of the ingest job that kicks this off.
  (let [[tm result] (util/time-execution
                     (indexer/reindex-provider-collections
                      context [provider-id] {:all-revisions-index? all-revisions-index?
                                             :refresh-acls? false
                                             :force-version? force-version?}))]
    (debug (format (str "Timed function %s handle-provider-event provider-collection-reindexing "
                        "all-revisions-index? %s took %d ms.") 
                   (str *ns*) all-revisions-index? tm))
    result))

(defmethod handle-provider-event :provider-autocomplete-suggestion-reindexing
  [context {:keys [provider-id]}]
  (autocomplete/reindex-autocomplete-suggestions-for-provider context provider-id))

(defmethod handle-provider-event :autocomplete-suggestion-prune
  [context _]
  (autocomplete/prune-stale-autocomplete-suggestions context))

(defmethod handle-provider-event :refresh-collection-granule-aggregation-cache
  [context {:keys [granules-updated-in-last-n]}]
  (cgac/refresh-cache context granules-updated-in-last-n))

(defmethod handle-provider-event :provider-delete
  [context {:keys [provider-id]}]
  (indexer/delete-provider context provider-id))

;; Default ignores the provider event. There may be provider events we don't care about.
(defmethod handle-provider-event :default
  [_ _])

(defmulti handle-ingest-event
  "Handle the various actions that can be requested via the indexing queue"
  (fn [_context _all-revisions-index? msg]
    (keyword (:action msg))))

;; Default ignores the ingest event. There may be ingest events we don't care about.
(defmethod handle-ingest-event :default
  [_ _ _])

(defmethod handle-ingest-event :concept-update
  [context all-revisions-index? {:keys [concept-id revision-id more-concepts]}]
  ;; combine concept-id revision-id with more-concepts
  (let [all-concepts (conj more-concepts {:concept-id concept-id :revision-id revision-id})
        [tm _result] (util/time-execution
                     (doseq [{:keys [concept-id revision-id]} all-concepts]
                       (if (= :humanizer (cc/concept-id->type concept-id))
                         (indexer/update-humanizers context)
                         (indexer/index-concept-by-concept-id-revision-id
                          context concept-id revision-id {:ignore-conflict? true
                                                          :all-revisions-index? all-revisions-index?}))))]
    (debug (format (str "Timed function %s handle-ingest-event concept-update for %d concepts for concept-id %s "
                        "revision-id %s all-revisions-index? %s took %d ms.")
                   (str *ns*) (count all-concepts) concept-id revision-id all-revisions-index? tm))))

(defmethod handle-ingest-event :concept-delete
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  ;(println "INSIDE handle-ingest-event JYNA with all-revisions-index = " all-revisions-index?)
  (when-not (= :humanizer (cc/concept-id->type concept-id))
    (let [[tm result] (util/time-execution
                        (indexer/delete-concept
                         context concept-id revision-id {:ignore-conflict? true
                                                         :all-revisions-index? all-revisions-index?}))]
      ;(println "Result of handle-ingest-event :concept-delete = " result)
      (debug (format (str "Timed function %s handle-ingest-event concept-delete for concept-id %s "
                          "revision-id %s all-revisions-index? %s took %d ms.")
                     (str *ns*) concept-id revision-id all-revisions-index? tm))
      result)))

(defmethod handle-ingest-event :tombstone-delete
  [context _all-revisions-index? {:keys [concept-id revision-id]}]
  (when (= :granule (cc/concept-id->type concept-id))
    (let [[tm result] (util/time-execution
                       (deleted-granule/remove-deleted-granule context concept-id revision-id
                                                               {:ignore-conflict? true}))]
      (debug (format "Timed function %s handle-ingest-event tombstone-delete for concept-id %s revision-id %s took %d ms."
                     (str *ns*) concept-id revision-id tm))
      result)))

(defmethod handle-ingest-event :concept-revision-delete
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  (when-not (= :humanizer (cc/concept-id->type concept-id))
    ;; We should never receive a message that's not for the all revisions index
    (when-not all-revisions-index?
      (errors/internal-error!
       (format (str "Received :concept-revision-delete event that wasn't for the all revisions "
                    "index.  concept-id: %s revision-id: %s")
               concept-id revision-id)))
    (let [[tm result] (util/time-execution
                       (indexer/force-delete-all-concept-revision context concept-id revision-id))]
      (debug (format (str "Timed function %s handle-ingest-event concept-revision-delete for concept-id %s "
                          "revision-id %s took %d ms.")
                     (str *ns*) concept-id revision-id tm))
      result)))

(defmethod handle-ingest-event :expire-concept
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  (when (= :granule (cc/concept-id->type concept-id))
    (let [[tm result] (util/time-execution
                       (indexer/delete-concept
                        context concept-id revision-id {:ignore-conflict? true
                                                        :all-revisions-index? all-revisions-index?}))]
      (debug (format (str "Timed function %s handle-ingest-event expire-concept for concept-id %s "
                          "revision-id %s all-revisions-index? %s took %d ms.")
                     (str *ns*) concept-id revision-id all-revisions-index? tm))
      result)))

(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [_ (config/provider-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/provider-queue-name)
                                #(handle-provider-event context %)))
    (dotimes [_ (config/index-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/index-queue-name)
                                #(handle-ingest-event context false %)))
    (dotimes [_ (config/all-revisions-index-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/all-revisions-index-queue-name)
                                #(handle-ingest-event context true %)))
    (dotimes [_ (config/deleted-granules-index-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/deleted-granule-index-queue-name)
                                #(handle-ingest-event context true %)))))
