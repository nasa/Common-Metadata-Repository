(ns cmr.virtual-product.services.virtual-product-service
  "Handles ingest events by filtering them to only events that matter for the virtual products and
  applies equivalent updates to virtual products."
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.ingest :as ingest]
            [cmr.virtual-product.config :as config]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.umm.core :as umm]
            [cmr.umm.granule :as umm-g]
            [cmr.common.mime-types :as mime-types]
            [cmr.common.concepts :as concepts]
            [cmr.common.services.errors :as errors]
            [clojure.set :as set]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.search :as search]
            [cmr.common.util :as u :refer [defn-timed]]))

(defmulti handle-ingest-event
  "Handles an ingest event. Checks if it is an event that should be applied to virtual granules. If
  it is then delegates to a granule event handler."
  (fn [context event]
    (keyword (:action event))))

(defmethod handle-ingest-event :default
  [context event]
  ;; Does nothing. We ignore events we don't care about.
  )

(defn subscribe-to-ingest-events
  "Subscribe to messages on the indexing queue."
  [context]
  (when (config/virtual-products-enabled)
    (let [queue-broker (get-in context [:system :queue-broker])
          queue-name (config/virtual-product-queue-name)]
      (dotimes [n (config/queue-listener-count)]
        (queue/subscribe queue-broker queue-name #(handle-ingest-event context %))))))

(def source-provider-id-entry-titles
  "A set of the provider id entry titles for the source collections."
  (-> config/source-to-virtual-product-config keys set))

(defn- annotate-event
  "Adds extra information to the event to help with processing"
  [{:keys [concept-id] :as event}]
  (let [{:keys [provider-id concept-type]} (concepts/parse-concept-id concept-id)]
    (-> event
        (update-in [:action] keyword)
        (assoc :provider-id provider-id :concept-type concept-type))))

(defn- virtual-granule-event?
  "Returns true if the granule identified by concept-type, provider-id and entry-title is virtual"
  [{:keys [concept-type provider-id entry-title]}]
  (and (= :granule concept-type)
       (contains? source-provider-id-entry-titles [provider-id entry-title])))

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

(defn-timed apply-source-granule-update-event
  "Applies a source granule update event to the virtual granules"
  [context {:keys [provider-id entry-title concept-id revision-id]}]
  (let [orig-concept (mdb/get-concept context concept-id revision-id)
        orig-umm (umm/parse-concept orig-concept)
        vp-config (config/source-to-virtual-product-config [provider-id entry-title])]

    (doseq [virtual-coll (:virtual-collections vp-config)]
      (let [new-granule-ur (config/generate-granule-ur provider-id
                                                       (:source-short-name vp-config)
                                                       (:short-name virtual-coll)
                                                       (:granule-ur orig-umm))
            new-umm (assoc orig-umm
                           :granule-ur new-granule-ur
                           :collection-ref (umm-g/map->CollectionRef
                                             (select-keys virtual-coll [:entry-title])))
            new-metadata (umm/umm->xml new-umm (mime-types/mime-type->format
                                                 (:format orig-concept)))
            new-concept (-> orig-concept
                            (select-keys [:format :provider-id :concept-type])
                            (assoc :native-id new-granule-ur
                                   :metadata new-metadata))
            headers     {"cmr-revision-id" revision-id
                         transmit-config/token-header (transmit-config/echo-system-token)}]
        (handle-update-response
          (ingest/ingest-concept context new-concept :is-raw true :headers headers)
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
  (let [{:keys [concept-id revision-id provider-id]} event
        granule-delete-concept (mdb/get-concept context concept-id revision-id)
        {{:keys [parent-collection-id granule-ur]} :extra-fields} granule-delete-concept
        parent-collection-concept (mdb/get-latest-concept context parent-collection-id)
        entry-title (get-in parent-collection-concept [:extra-fields :entry-title])]
    (assoc event
           :granule-ur granule-ur
           :entry-title entry-title)))

(defn- handle-delete-response
  "Handle response received from ingest service to a delete request. Status codes which do not
  fall between 200 and 299 or not equal to 409 will cause an exception which in turn causes the
  corresponding queue event to be put back in the queue to be retried."
  [response granule-ur]
  (let [{:keys [status body]} response]
    (cond
      (<= 200 status 299)
      (info (format "Deleted virtual granule [%s] with the following response: [%s]"
                    granule-ur (pr-str body)))

      ;; Not Found (status code 404)
      ;; This would occur if delete event is consumed before the concept creation event and
      ;; metadata-db does not yet have the granule concept. This usually means that an ingest event
      ;; for the same granule is present in the virtual product queue and is not yet consumed. The
      ;; exception will cause the event to be put back in the queue. We don't ignore this because
      ;; the create event would eventually be processed and the virtual granule would exist when it
      ;; should actually be deleted. We will retry this until the granule is created and the
      ;; deletion can be processed successfully creating a tombstone with the correct revision id.

      ;; Note that the existence of a delete event for the virtual granule means that the
      ;; corresponding real granule exists in the database (otherwise corresponding delete request
      ;; for real granule would fail with 404 and the delete event for the virtual granule would
      ;; never be put on the virtual product queue to begin with). Which in turn means that ingest
      ;; event corresponding to the granule has been created in the past (either recent past or
      ;; distant past) and that event has to to consumed before the delete event can be consumed
      ;; off the queue.
      (= status 404)
      (errors/internal-error!
        (format (str "Received a response with status code [404] and the following response body "
                     "when deleting the virtual granule [%s] : [%s]. The delete request will be "
                     "retried.")
                granule-ur (pr-str body)))

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
                status granule-ur (pr-str response))))))

(defn-timed apply-source-granule-delete-event
  "Applies a source granule delete event to the virtual granules"
  [context {:keys [provider-id revision-id granule-ur entry-title]}]
  (let [vp-config (config/source-to-virtual-product-config [provider-id entry-title])]
    (doseq [virtual-coll (:virtual-collections vp-config)]
      (let [new-granule-ur (config/generate-granule-ur provider-id
                                                       (:source-short-name vp-config)
                                                       (:short-name virtual-coll)
                                                       granule-ur)
            headers     {"cmr-revision-id" revision-id
                         transmit-config/token-header (transmit-config/echo-system-token)}
            resp (ingest/delete-concept context {:provider-id provider-id
                                                 :concept-type :granule
                                                 :native-id new-granule-ur}
                                        :is-raw true :headers headers)]
        (handle-delete-response resp new-granule-ur)))))

(defmethod handle-ingest-event :concept-delete
  [context event]
  (when (config/virtual-products-enabled)
    (let [annotated-event (annotate-event event)]
      (when (= :granule (:concept-type annotated-event))
        (let [annotated-delete-event (annotate-granule-delete-event context annotated-event)]
          (when (virtual-granule-event? annotated-delete-event)
            (apply-source-granule-delete-event context annotated-delete-event)))))))

(defn- annotate-entries
  "Annotate entries with provider id derived from concept-id present in the entry"
  [entries]
  (for [entry entries
        :let [{:keys [provider-id]} (concepts/parse-concept-id (:concept-id entry))]]
    (with-meta entry {:provider-id provider-id})))

(defn- filter-virtual-entries
  "Filter to retrieve virtual entries from the input entries."
  [entries]
  (for [entry entries
        :let [entry-title (:entry-title entry)
              provider-id (:provider-id (meta entry))]
        :when (contains? config/virtual-product-to-source-config [provider-id entry-title])]
    entry))

(defn- compute-source-granule-urs
  "Compute source granule-urs from virtual granule-urs"
  [provider-id virtual-granule-entries]
  (for [{:keys [granule-ur entry-title]} virtual-granule-entries]
    (let [{:keys [source-short-name short-name]}
          (get config/virtual-product-to-source-config [provider-id entry-title])]
      (config/compute-source-granule-ur
        provider-id source-short-name short-name granule-ur))))

(defn- create-source-entries
  "Fetch granule ids from the granule urs of granules that belong to a collection with the given
  provider id and entry title and create the entries using the information."
  [context provider-id entry-title granule-urs]
  (for [[granule-ur granule-id] (search/find-granule-ids
                                  context provider-id entry-title granule-urs)]
    {:concept-id granule-id
     :entry-title entry-title
     :granule-ur granule-ur}))

(defn- group-by-source-entry-title
  "Function used for grouping granule entries by entry title. groups is a map with keys like this:
  [provider-id entry-title] and values which are an array of granule entries all of which have the
  same entry-title as in the key. This function adds the given entry into the right key group. This
  function is used with-in a reduce function."
  [groups entry]
  (let [provider-id (:provider-id (meta entry))
        source-entry-title (get-in config/virtual-product-to-source-config
                                   [[provider-id (:entry-title entry)] :source-entry-title])]
    (if (contains? groups source-entry-title)
      (update-in groups [[provider-id source-entry-title]] conj entry)
      (assoc groups [provider-id source-entry-title] [entry]))))

(defn- virtual-entries->source-entries
  "Translate the virtual entries to the corresponding source entries."
  [context virtual-entries]
  (flatten
    (let [entries-by-source-entry-title (reduce group-by-source-entry-title {} virtual-entries)]
      (for [[[provider-id source-entry-title] granule-entries] entries-by-source-entry-title]
        (let [source-granule-urs (set (compute-source-granule-urs provider-id granule-entries))]
          (create-source-entries context provider-id source-entry-title source-granule-urs))))))

(defn translate-granule-entries
  "Translate virtual granules in the granule-entries into the corresponding source entries. Remove
  the duplicates from the final set of entries.See routes.clj for the JSON schema of
  granule-entries."
  [context granule-entries]
  (let [annotated-entries (set (annotate-entries granule-entries))
        virtual-entries (set (filter-virtual-entries annotated-entries))
        non-virtual-entries (set/difference annotated-entries virtual-entries)
        translated-virtual-entries (set (virtual-entries->source-entries context virtual-entries))]
    (set/union non-virtual-entries translated-virtual-entries)))
