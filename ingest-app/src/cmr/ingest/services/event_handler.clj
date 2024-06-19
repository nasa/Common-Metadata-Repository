(ns cmr.ingest.services.event-handler
 (:require
  [cmr.ingest.config :as config]
  [cmr.ingest.services.bulk-update-service :as bulk-update]
  [cmr.ingest.services.granule-bulk-update-service :as granule-bulk-update-service]
  [cmr.message-queue.queue.queue-protocol :as queue-protocol]))

(defmulti handle-provider-event
  "Handle the various actions that can be requested for a provider via the provider-queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-provider-event :bulk-update
  [context msg]
  (bulk-update/handle-bulk-update-event
   context
   (:provider-id msg)
   (:task-id msg)
   (:bulk-update-params msg)
   (:user-id msg)))

(defmethod handle-provider-event :collection-bulk-update
  [context msg]
  (bulk-update/handle-collection-bulk-update-event
   context
   (:provider-id msg)
   (:task-id msg)
   (:concept-id msg)
   (:bulk-update-params msg)
   (:user-id msg)))

(defmethod handle-provider-event :granules-bulk
  [context message]
  (granule-bulk-update-service/handle-granules-bulk-event
   context
   (:provider-id message)
   (:task-id message)
   (:bulk-update-params message)
   (:user-id message)))

;; TODO Step 7 handles the granule bulk update event ??
;; Example message:
;; {
;;  "action":"granule-bulk-update",
;;  "provider-id":"CDDIS",
;;  "task-id":670092,
;;  "bulk-update-params":{
;;    "event-type":"update_field:onlineaccessurl",
;;    "granule-ur":"gnss_data_highrate_2009_113_09d_00_darw113a15.09d.Z",
;;    "new-value":[{
;;                  "from":"https://cddis.nasa.gov/archive/gnss/data/highrate/2009/113/09d/00/darw113a15.09d.Z",
;;                  "to":"https://cddis.nasa.gov/archive/gnss/data/highrate/2009/113/darw1130.09d.txt"
;;                  },
;;                  {
;;                   "from":"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2009/113/09d/00/darw113a15.09d.Z",
;;                    "to":"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2009/113/darw1130.09d.txt"
;;                  }]},
;;    "user-id":"taylor.yates"
;; }
(defmethod handle-provider-event :granule-bulk-update
  [context message]
  (granule-bulk-update-service/handle-granule-bulk-update-event
   context
   (:provider-id message)
   (:task-id message)
   (:bulk-update-params message)
   (:user-id message)))

(defmethod handle-provider-event :granule-bulk-update-task-cleanup
  [context message]
  (granule-bulk-update-service/cleanup-bulk-granule-task-table context))

;; Default ignores the provider event. There may be provider events we don't care about.
(defmethod handle-provider-event :default
  [_ _])

;; TODO Step 6 where we ingest from queue
(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/ingest-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/ingest-queue-name)
                                #(handle-provider-event context %)))
    (dotimes [n (config/bulk-update-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/bulk-update-queue-name)
                                #(handle-provider-event context %)))))
