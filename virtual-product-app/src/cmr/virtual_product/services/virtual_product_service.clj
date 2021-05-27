(ns cmr.virtual-product.services.virtual-product-service
  "Handles ingest events by filtering them to only events that matter for the virtual products and
  applies equivalent updates to virtual products."
  (:require
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as u :refer [defn-timed]]
   [cmr.message-queue.config :as queue-config]
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.ingest :as ingest]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm.umm-core :as umm]
   [cmr.umm.umm-granule :as umm-g]
   [cmr.virtual-product.config :as config]
   [cmr.virtual-product.data.source-to-virtual-mapping :as svm]))

(def ^:private num-retries-delete-not-found
  "Number of retries for not found error during deletion of source granule.
  The default CMR RabbitMQ retries 5 times with increasing delays as in [5, 50, 500, 5000, 50000].
  It takes too long to exhaust the retries when deleting virtual granules as some of the virtual
  granules will not be generated for some source granules (e.g. AST_L1A) and we will always get
  the 404 status code back. This setting shortens the wait time in this case and make the virtual
  granule deletion more prompt."
  2)

(defmulti handle-ingest-event
  "Handles an ingest event. Checks if it is an event that should be applied to virtual granules. If
  it is then delegates to a granule event handler."
  (fn [context event]
    (keyword (:action event))))

(defmethod handle-ingest-event :default
  [context event])
  ;; Does nothing. We ignore events we don't care about.


(defn subscribe-to-ingest-events
  "Subscribe to messages on the indexing queue."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])
        queue-name (config/virtual-product-queue-name)]
    (dotimes [n (config/queue-listener-count)]
      (queue-protocol/subscribe queue-broker queue-name #(handle-ingest-event context %)))))

(def source-provider-id-entry-titles
  "A set of the provider id entry titles for the source collections."
  (-> svm/source-to-virtual-product-mapping keys set))

(defn- annotate-event
  "Adds extra information to the event to help with processing"
  [{:keys [concept-id] :as event}]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (-> event
        (update-in [:action] keyword)
        (assoc :provider-id provider-id
               :concept-type concept-type))))

(defn- virtual-granule-event?
  "Returns true if the granule identified by concept-type, provider-id and entry-title is virtual"
  [{:keys [concept-type provider-id entry-title]}]
  (and (= :granule concept-type)
       (contains? source-provider-id-entry-titles
                  [(svm/provider-alias->provider-id provider-id) entry-title])
       (not (contains? (config/disabled-virtual-product-source-collections) entry-title))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handle updates

(defn- handle-update-response
  "Handle response received from ingest service to an update request. Status codes which do not
  fall between 200 and 299 or not equal to 409 will cause an exception which in turn causes the
  corresponding queue event to be put back in the queue to be retried."
  [response granule-ur]
  (let [{:keys [status body]} response]
    (cond
      (<= 200 status 299)
      (info (format "Ingested virtual granule [%s] with the following response: [%s]"
                    granule-ur (pr-str body)))

      ;; Conflict (status code 409)
      ;; This would occurs when an ingest event with lower revision-id is consumed after an event with
      ;; higher revision id for the same granule. The event is ignored and the revision is lost.
      (= status 409)
      (info (format (str "Received a response with status code [409] and the following body when "
                         "ingesting the virtual granule [%s] : [%s]. The event will be ignored.")
                    granule-ur (pr-str body)))

      :else
      (errors/internal-error!
        (format (str "Received unexpected status code [%s] and the following response when "
                     "ingesting the virtual granule [%s] : [%s]")
                status granule-ur (pr-str response))))))

(def virtual-product-client-id
  "Client Id used by Virtual Product Service"
  "Virtual-Product-Service")

(defn- build-ingest-headers
  "Create http headers which will be part of ingest requests send to ingest service"
  [revision-id]
  {"cmr-revision-id" revision-id
   transmit-config/token-header (transmit-config/echo-system-token)
   "Client-Id" virtual-product-client-id})

(defn source-granule-matches-virtual-product?
  "Check if the source granule umm matches with the matcher for the virtual collection under
  the given provider and with the given entry title"
  [provider-id virt-entry-title src-granule-umm]
  (let [matcher (:matcher (get svm/virtual-product-to-source-mapping
                               [provider-id virt-entry-title]))]
    (or (nil? matcher) (matcher src-granule-umm))))

(defn-timed apply-source-granule-update-event
  "Applies a source granule update event to the virtual granules"
  [context {:keys [provider-id entry-title concept-id revision-id]}]
  (let [provider-id (svm/provider-alias->provider-id provider-id)
        orig-concept (mdb/get-concept context concept-id revision-id)
        format (-> (:format orig-concept)
                   mime-types/base-mime-type-of)
        ;; If the format is umm-json, we need to use umm-spec to parse the metadata into UMM.
        orig-umm (if (= format mime-types/umm-json)
                   (umm-spec/parse-metadata context (:concept-type orig-concept) format (:metadata orig-concept))
                   (umm/parse-concept orig-concept))
        vp-config (svm/source-to-virtual-product-mapping [provider-id entry-title])
        source-short-name (:short-name vp-config)]
    (doseq [virtual-coll (:virtual-collections vp-config)
            :when (source-granule-matches-virtual-product?
                    provider-id (:entry-title virtual-coll) orig-umm)]
      (let [new-umm (svm/generate-virtual-granule-umm provider-id source-short-name
                                                      orig-umm virtual-coll)
            new-granule-ur (:granule-ur new-umm)
            new-metadata (if (= format mime-types/umm-json)
                           (umm-spec/generate-metadata context new-umm (mime-types/mime-type->format
                                                                         (:format orig-concept)))
                           (umm/umm->xml new-umm (mime-types/mime-type->format
                                                   (:format orig-concept))))
            new-concept (-> orig-concept
                            (select-keys [:format :provider-id :concept-type])
                            (assoc :native-id new-granule-ur
                                   :metadata new-metadata))]
        (handle-update-response
          (ingest/ingest-concept context new-concept (build-ingest-headers revision-id) true)
          new-granule-ur)))))

(defmethod handle-ingest-event :concept-update
  [context event]
  (when (config/virtual-products-enabled)
    (let [annotated-event (annotate-event event)]
      (when (virtual-granule-event? annotated-event)
        (apply-source-granule-update-event context annotated-event)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handle deletes

(defn- annotate-granule-delete-event
  "Adds extra information to the granule delete event to aid in processing."
  [context event]
  (let [{:keys [concept-id revision-id]} event
        granule-delete-concept (mdb/get-concept context concept-id revision-id)
        {{:keys [parent-collection-id granule-ur]} :extra-fields} granule-delete-concept
        parent-collection-concept (mdb/get-latest-concept context parent-collection-id)
        entry-title (get-in parent-collection-concept [:extra-fields :entry-title])]
    (assoc event
           :granule-ur granule-ur
           :entry-title entry-title)))

(defn- handle-delete-response
  "Handle response received from ingest service to a delete request. Returns true if the source
  granule deletion event need to be retried. Status codes which do not fall between 200 and 299
  or not equal to 404 or 409 will cause an exception which in turn causes the corresponding queue
  event to be put back in the queue to be retried indefinitely. Status code 404 will be retried
  twice. Other status codes are treated as success."
  [response granule-ur retry-count]
  (let [{:keys [status body]} response
        retry-count (or retry-count 0)
        needs-retry (when (and (= status 404)
                               (< retry-count num-retries-delete-not-found))
                      true)]
    (cond
      (<= 200 status 299)
      (info (format "Deleted virtual granule [%s] with the following response: [%s]"
                    granule-ur (pr-str body)))

      ;; Not Found (status code 404)
      ;; This would occur in two different scenarios:
      ;; 1) Out of order processing with delete event consumed before ingest event when it should
      ;;    be other way round. The exception below will cause the event to be put back in the
      ;;    queue. We don't ignore the error response since otherwise the create event would
      ;;    eventually be processed and the virtual granule would ultimately be in undeleted state
      ;;    when it should have been deleted. The delete event will be retried until the
      ;;    granule is created and the deletion can eventually be processed successfully creating a
      ;;    tombstone with the correct revision id. The assumption here is that ingest event will
      ;;    be processed before the maximum number of retries for delete event is reached. If this
      ;;    does not happen, the granule will end up in inconsistent state.
      ;; 2) delete event corresponds to delete of a source granule for which a UMM matcher would return
      ;;    a truth value of false. For such a source granule, the corresponding virtual granule
      ;;    wouldn't even be created in the first place. Delete handler has no way of checking the
      ;;    truth value and sends delete request for the virtual granule even if the vitual granule
      ;;    doesn't really exist. These events will be retried for as many times a the configured
      ;;    maximum number of retries for an event. After the retries, the delete event will be
      ;;    considered as a success.
      (= status 404)
      (if (< retry-count num-retries-delete-not-found)
        (info (format (str "Received a response with status code [404] and the following "
                           "response body when deleting the virtual granule [%s] : [%s]. "
                           "The delete request will be retried.")
                      granule-ur (pr-str body)))
        (info (format (str "The number of retries has exceeded the maximum retry count."
                           "The delete event for the virtual granule [%s] will be ignored")
                      granule-ur)))

      ;; Conflict (status code 409)
      ;; This would occurs if a delete event with lower revision-id is consumed after an event with
      ;; higher revision id for the same granule. The event is ignored and the revision is lost.
      (= status 409)
      (info (format (str "Received a response with status code [409] and following body when
                         deleting the virtual granule [%s] : [%s]. The event will be ignored")
                    granule-ur (pr-str body)))

      :else
      (errors/internal-error!
        (format (str "Received unexpected status code [%s] and the following response when "
                     "deleting the virtual granule [%s] : [%s]")
                status granule-ur (pr-str response))))
    needs-retry))

(defn-timed apply-source-granule-delete-event
  "Applies a source granule delete event to the virtual granules"
  [context {:keys [provider-id revision-id granule-ur entry-title retry-count]}]
  (let [vp-config (svm/source-to-virtual-product-mapping
                    [(svm/provider-alias->provider-id provider-id) entry-title])
        virtual-colls (:virtual-collections vp-config)
        delete-virtual-granule (fn [virtual-coll]
                                 (let [new-granule-ur (svm/generate-granule-ur
                                                        provider-id
                                                        (:short-name vp-config)
                                                        virtual-coll
                                                        granule-ur)
                                       resp (ingest/delete-concept
                                              context
                                              {:provider-id provider-id
                                               :concept-type :granule
                                               :native-id new-granule-ur}
                                              (build-ingest-headers revision-id) true)]
                                   (handle-delete-response resp new-granule-ur retry-count)))
        need-retries (doall (map delete-virtual-granule virtual-colls))]
    (when (some true? need-retries)
      (errors/internal-error!
        (format "Need to retry deletion of source granule [%s] due to virtual granule deletion warnings"
                granule-ur)))))

(defmethod handle-ingest-event :concept-delete
  [context event]
  (when (config/virtual-products-enabled)
    (let [annotated-event (annotate-event event)]
      (when (= :granule (:concept-type annotated-event))
        (let [annotated-delete-event (annotate-granule-delete-event context annotated-event)]
          (when (virtual-granule-event? annotated-delete-event)
            (apply-source-granule-delete-event context annotated-delete-event)))))))
