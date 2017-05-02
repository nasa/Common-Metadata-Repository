(ns cmr.ingest.services.bulk-update-service
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.data.bulk-update :as data-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]))

(def bulk-update-schema
  (js/json-string->json-schema (slurp (io/resource "bulk_update_schema.json"))))

(defn validate-bulk-update-post-params
  "Validate post body for bulk update. Validate against schema and validation
  rules."
  [json]
  (js/validate-json! bulk-update-schema json)
  (let [body (json/parse-string json true)
        {:keys [update-type update-value find-value]} body]
    (when (and (not= "CLEAR_FIELD" update-type)
               (not= "FIND_AND_REMOVE" update-type)
               (nil? update-value))
      (errors/throw-service-errors :bad-request
                                   [(format "An update value must be supplied when the update is of type %s"
                                            update-type)]))
    (when (and (or (= "FIND_AND_REPLACE" update-type)
                   (= "FIND_AND_REMOVE" update-type))
               (nil? find-value))
      (errors/throw-service-errors :bad-request
                                   [(format "A find value must be supplied when the update is of type %s"
                                            update-type)]))))

(defn validate-and-queue-bulk-update
  "Validate the bulk update POST parameters and queueu bulk update"
  [context provider-id json]
  (validate-bulk-update-post-params json)
  (ingest-events/publish-provider-event context
    (ingest-events/provider-bulk-update-event provider-id json)))

(defn handle-bulk-update-event
  "Write to tables and queueu collection bulk update messages"
  [context provider-id bulk-update-params]
  (let [body (json/parse-string bulk-update-params true)
        {:keys [concept-ids]} body
        task-id (data-bulk-update/create-bulk-update-status context
                 provider-id bulk-update-params concept-ids)]
    (doseq [concept-id concept-ids]
     (ingest-events/publish-provider-event context
       (ingest-events/provider-collection-bulk-update-event
         task-id concept-id body)))))

(defn handle-collection-bulk-update-event
  "Perform update for the given concept id"
  [context task-id concept-id bulk-update-params]
  (data-bulk-update/update-bulk-update-task-collection-status context task-id concept-id "COMPLETE" nil))
