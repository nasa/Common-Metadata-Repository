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
   [cmr.ingest.data.bulk-update :as data-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.field-update :as field-update])

 (defn handle-bulk-update-event
  "For each granule-ur, queue bulk update messages"
  [context provider-id task-id bulk-update-params user-id]
  (let [{:keys [concept-ids]} bulk-update-params]
    (doseq [granule-ur concept-ids]
     (ingest-events/publish-gran-bulk-update-event
      context
      (ingest-events/ingest-granule-bulk-update-event
       provider-id
       task-id
       granule-ur
       bulk-update-params
       user-id))))))

(defn validate-and-save-bulk-granule-update
 "Validate the bulk update POST parameters, save rows to the db for task
 and granule statuses, and queue bulk granule update. Return task id, which comes
 from the db save."
 [context provider-id json user-id]
 (let [bulk-update-params (json/parse-string json true)
       {:keys [concept-ids]} bulk-update-params
       bulk-update-params (assoc bulk-update-params :concept-ids concept-ids)
       task-id (errors/throw-service-errors
                      :invalid-data
                      [(str "Error creating bulk update task: "
                            "This functionality is not yet implemented")])]
   ;; Queue the bulk update event
   (ingest-events/publish-ingest-event
     context
     (ingest-events/ingest-bulk-update-event provider-id task-id bulk-update-params user-id))
   task-id))
