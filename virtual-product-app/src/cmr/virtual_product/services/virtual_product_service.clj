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
            [clojure.string :as str]
            [cmr.common.mime-types :as mime-types]
            [cmr.common.concepts :as concepts]))

(def source-provider-id-entry-titles
  "A set of the provider id entry titles for the source collections."
  (-> config/source-to-virtual-product-config keys set))

(defmulti handle-virtual-granule-event
  "Multimethod that handles a virtual granule event depending on which type of event it was."
  (fn [context event]
    (:action event)))

(defmethod handle-virtual-granule-event :concept-update
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
                            (select-keys [:revision-id :format :provider-id :concept-type])
                            (assoc :native-id new-granule-ur
                                   :metadata new-metadata))
            resp (ingest/ingest-concept context new-concept)]
        (info (format "Ingested new virtual granule [%s] with response [%s]"
                      new-granule-ur (pr-str resp)))))))

(defmethod handle-virtual-granule-event :concept-delete
  [context {:keys [concept-id revision-id]}]
  (warn "Ignoring granule update temporarily"))

(defn- annotate-event
  "Adds extra information to the event to help with processing"
  [{:keys [concept-id] :as event}]
  (let [{:keys [provider-id concept-type]} (concepts/parse-concept-id concept-id)]
    (-> event
        (update-in [:action] keyword)
        (assoc :provider-id provider-id :concept-type concept-type))))

(defn- concept-event?
  "Returns true if this is an event that applies to concepts"
  [event]
  (contains? #{:concept-update :concept-delete :concept-create} (keyword (:action event))))

(defn- virtual-granule-event?
  "Returns true if this is an event that should apply to virtual granules"
  [{:keys [concept-type provider-id entry-title]}]
  (and (= :granule concept-type)
       (contains? source-provider-id-entry-titles [provider-id entry-title])))

(defn- handle-ingest-event
  "Handles an ingest event. Checks if it is an event that should be applied to virtual granules. If
  it is then delegates to a granule event handler."
  [context event]
  (when (and (config/virtual-products-enabled)
             (concept-event? event))
    (let [annotated-event (annotate-event event)]
      (when (virtual-granule-event? annotated-event)
        (handle-virtual-granule-event context annotated-event)))))

(defn subscribe-to-ingest-events
  "Subscribe to messages on the indexing queue."
  [context]
  (when (config/virtual-products-enabled)
    (let [queue-broker (get-in context [:system :queue-broker])
          queue-name (config/virtual-product-queue-name)]
      (dotimes [n (config/queue-listener-count)]
        (queue/subscribe queue-broker queue-name #(handle-ingest-event context %))))))



