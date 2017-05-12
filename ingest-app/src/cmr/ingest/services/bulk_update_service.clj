(ns cmr.ingest.services.bulk-update-service
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.data.bulk-update :as data-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.field-update :as field-update]))

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

(defn validate-and-save-bulk-update
  "Validate the bulk update POST parameters, save rows to the db for task
  and collection statuses, and queueu bulk update. Return task id, which comes
  from the db save."
  [context provider-id json]
  (validate-bulk-update-post-params json)
  (let [bulk-update-params (json/parse-string json true)
        {:keys [concept-ids]} bulk-update-params
        ;; Write db rows - one for overall status, one for each concept id
        task-id (data-bulk-update/create-bulk-update-task context
                 provider-id json concept-ids)]
    ;; Queue the bulk update event
    (ingest-events/publish-ingest-event context
      (ingest-events/ingest-bulk-update-event task-id bulk-update-params))
    task-id))

(defn handle-bulk-update-event
  "For each concept-id, queueu collection bulk update messages"
  [context task-id bulk-update-params]
  (let [{:keys [concept-ids]} bulk-update-params]
    (doseq [concept-id concept-ids]
     (ingest-events/publish-ingest-event context
       (ingest-events/ingest-collection-bulk-update-event
         task-id concept-id bulk-update-params)))))

(defn- update-collection
  "Perform the update on the collection"
  [context concept bulk-update-params]
  (let [{:keys [update-type update-field find-value update-value]} bulk-update-params
        update-type (csk/->kebab-case-keyword update-type)
        update-field (csk/->PascalCaseKeyword update-field)]
    (field-update/update-concept context concept update-type update-field
                                 update-value find-value)))


(defn- update-and-save-collection
  "Make collection updates, go through ingest validation. Attempt save to
  metadata db. If validation or save errors are thrown, they will be caught and
  handled in handle-collection-bulk-update-event. If warnings were returned,
  return a warning string."
  [context concept bulk-update-params]
  (let [updated-metadata (update-collection context concept bulk-update-params)
        concept (-> concept
                    (assoc :metadata updated-metadata)
                    (update :revision-id inc))
        ;; If errors are caught, an error will be thrown and logged to the DB
        ;; If we get warnings back, validation was successful, but will still
        ;; log warnings
        {:keys [concept warnings]} (ingest-service/validate-and-prepare-collection
                                     context concept nil)]
    (mdb/save-concept context concept)
    (when (seq warnings)
      (str "Collection was updated successfully, but translating the collection "
           "to UMM-C had the following issues: "
           (str/join "; " warnings)))))

(defn handle-collection-bulk-update-event
  "Perform update for the given concept id. Log an error status of the concept
  cannot be found."
  [context task-id concept-id bulk-update-params]
  (try
    (if-let [concept (mdb2/get-latest-concept context concept-id)]
      (let [success-status-message (update-and-save-collection context concept bulk-update-params)]
        (data-bulk-update/update-bulk-update-task-collection-status context task-id
          concept-id "COMPLETE" success-status-message))
      (data-bulk-update/update-bulk-update-task-collection-status context task-id
        concept-id "FAILED" (format "Concept-id [%s] is not valid." concept-id)))
    (catch clojure.lang.ExceptionInfo ex-info
      (if (= :conflict (:type (.getData ex-info)))
        ;; Concurrent update - re-queue concept update
        (ingest-events/publish-ingest-event context
          (ingest-events/ingest-collection-bulk-update-event
            task-id concept-id bulk-update-params))
        (data-bulk-update/update-bulk-update-task-collection-status
          context task-id concept-id "FAILED" (.getMessage ex-info))))
    (catch Exception e
      (let [message (.getMessage e)
            concept-id-message (re-find #"Concept-id.*is not valid." message)]
        (if concept-id-message
          (data-bulk-update/update-bulk-update-task-collection-status context task-id concept-id "FAILED" concept-id-message)
          (data-bulk-update/update-bulk-update-task-collection-status context task-id concept-id "FAILED" message))))))
