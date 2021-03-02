(ns cmr.ingest.services.granule-bulk-update-service
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common-app.config :as common-config]
   [cmr.common.concepts :as concepts]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.data.granule-bulk-update :as data-granule-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.ingest.services.bulk-update-service :as bulk-update-service]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.field-update :as field-update]))

(def bulk-granule-update-schema
 (js/json-string->json-schema (slurp (io/resource "granule_bulk_update_schema.json"))))

(defmethod bulk-update-service/validate-bulk-update-post-params :granule
 [_ json]
 (js/validate-json! bulk-granule-update-schema json))

(defn handle-bulk-update-event
  "For each granule-ur, queue bulk update messages"
  [context provider-id task-id bulk-update-params]
  (let [{:keys [updates operation update-field]} bulk-update-params]
    (doseq [update updates]
      (let [granule-ur (first update)
            update-value (second update)
            event-type (str operation ":" update-field)]
       (ingest-events/publish-gran-bulk-update-event
        context
        (ingest-events/ingest-granule-bulk-update-event
         provider-id
         task-id
         event-type
         granule-ur
         update-value))))))

(defn validate-and-save-bulk-granule-update
 "Validate the bulk update POST parameters, save rows to the db for task
 and granule statuses, and queue bulk granule update. Return task id, which comes
 from the db save."
 [context provider-id json user-id]
 (bulk-update-service/validate-bulk-update-post-params :granule json)
 (let [bulk-update-params (json/parse-string json true)
       task-id (try
                 (data-granule-bulk-update/create-bulk-update-task
                  context
                  provider-id
                  user-id
                  json)
                 (catch Exception e
                   (let [message (.getMessage e)
                         user-facing-message (if (string/includes? message "BULK_UPDATE_TASK_STATUS_UK")
                                               "Bulk update task name needs to be unique within the provider."
                                               message)]
                     (errors/throw-service-errors
                      :invalid-data
                      [(str "error creating bulk update task: " user-facing-message)]))))]
   ;; Queue the bulk update event
   (handle-bulk-update-event context provider-id task-id bulk-update-params)
   task-id))

(defn handle-granule-bulk-update-event
  "Skeleton handler function"
  [context provider-id task-id bulk-update-params user-id]
  (let [{:keys [granule-ur]} bulk-update-params]
    (data-granule-bulk-update/update-bulk-update-granule-status
     context
     task-id
     granule-ur
     bulk-update-service/failed-status
     (format "Granule-ur [%s] is not associated with provider-id [%s]." granule-ur provider-id))))
