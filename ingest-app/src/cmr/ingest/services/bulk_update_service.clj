(ns cmr.ingest.services.bulk-update-service
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common-app.config :as common-config]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.data.bulk-update :as data-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.field-update :as field-update]))

(def bulk-update-schema
  (js/json-string->json-schema (slurp (io/resource "bulk_update_schema.json"))))

(def default-exception-message
  "There was an error updating the concept.")

(def update-format
  "Format to save bulk updates"
  (str "application/vnd.nasa.cmr.umm+json;version=" (common-config/collection-umm-version)))

(def complete-status
  "Indicates bulk update operation finished."
  "COMPLETE")

(def updated-status
  "Indicates the collection is updated."
  "UPDATED")

(def failed-status
  "Indicates bulk update operation completed with errors"
  "FAILED")

(def skipped-status
  "Indicates bulk update operation is skipped because no find-value is found."
  "SKIPPED")

(def add-to-existing
  "Represents ADD_TO_EXISTING update type"
  "ADD_TO_EXISTING")

(def clear-all-and-replace
  "Represents CLEAR_ALL_AND_REPLACE update type"
  "CLEAR_ALL_AND_REPLACE")

(def find-and-replace
  "Represents FIND_AND_REPLACE update type"
  "FIND_AND_REPLACE")

(def find-and-remove
  "Represents FIND_AND_REMOVE update type"
  "FIND_AND_REMOVE")

(def find-and-update
  "Represents FIND_AND_UPDATE update type"
  "FIND_AND_UPDATE")

(def find-and-update-home-page-url
  "Represents FIND_AND_UPDATE_HOME_PAGE_URL update type"
  "FIND_AND_UPDATE_HOME_PAGE_URL")

(def data-centers
  "Represents DATA_CENTERS update field"
  "DATA_CENTERS")

(defn validate-bulk-update-post-params
  "Validate post body for bulk update. Validate against schema and validation
  rules."
  [json]
  (js/validate-json! bulk-update-schema json)
  (let [body (json/parse-string json true)
        {:keys [update-type update-value find-value update-field]} body]
    (when (and (not= find-and-remove update-type)
               (nil? update-value))
      (errors/throw-service-errors :bad-request
                                   [(format "An update value must be supplied when the update is of type %s"
                                            update-type)]))
    (when (and (= find-and-update-home-page-url update-type)
               (not= data-centers update-field))
      (errors/throw-service-errors
       :bad-request
       [(str find-and-update-home-page-url " update type can not be used for the [" update-field
             "] update field. "
             "It can only be used for the " data-centers " update field.")]))
    (when (and (not= add-to-existing update-type)
               (not= clear-all-and-replace update-type)
               (not= find-and-replace update-type)
               (sequential? update-value))
      (errors/throw-service-errors
        :bad-request
        [(str "An update value must be a single object for the [" update-type "] update type. "
              "Arrays are only supported for the " add-to-existing ", " clear-all-and-replace
              " and " find-and-replace " update types.")]))
    (when (and (or (= find-and-replace update-type)
                   (= find-and-remove update-type)
                   (= find-and-update update-type))
               (nil? find-value))
      (errors/throw-service-errors :bad-request
                                   [(format "A find value must be supplied when the update is of type %s"
                                            update-type)]))))

(defn- get-provider-collection-concept-ids
  "Returns a list of collection concept ids for a given provider-id.
   Raise error if no collections are found."
  [context provider-id]
  (let [collections (mdb/find-collections context
                                          {:provider-id provider-id
                                           :latest true})
        collections (remove :deleted collections)]
    (if (seq collections)
      (map :concept-id collections)
      (errors/throw-service-errors
        :bad-request
        [(format "There are no collections that have not been deleted for provider [%s]." provider-id)]))))

(defn- get-collection-concept-id-validation-err-msgs
  "Returns the concept-id validation msgs"
  [concept-ids]
  (for [id concept-ids
        :let [err-msg (concepts/concept-id-validation id)
              err-msg
               (if err-msg
                 err-msg
                 (when-not (string/starts-with? id "C")
                   [(format "Collection concept-id [%s] is invalid, must start with C" id)]))]
        :when err-msg]
    err-msg))

(defn- validate-concept-ids
  "Validate concept-ids. Raise error if there exist invalid concept-ids."
  [concept-ids]
  (when-not (= ["ALL"] (map string/upper-case concept-ids))
    (let [err-msgs (get-collection-concept-id-validation-err-msgs concept-ids)]
      (when (seq err-msgs)
        (errors/throw-service-errors
          :bad-request
          [(string/join ", " err-msgs)])))))

(defn- get-concept-ids
  "Get the concept-ids from either the concept-ids passed in or
   from the provider."
  [context concept-ids provider-id]
  (if (= ["ALL"] (map string/upper-case concept-ids))
    (get-provider-collection-concept-ids context provider-id)
    concept-ids))

(defn validate-and-save-bulk-update
  "Validate the bulk update POST parameters, save rows to the db for task
  and collection statuses, and queue bulk update. Return task id, which comes
  from the db save."
  [context provider-id json user-id]
  (validate-bulk-update-post-params json)
  (let [bulk-update-params (json/parse-string json true)
        {:keys [concept-ids]} bulk-update-params
        _ (validate-concept-ids concept-ids)
        concept-ids (get-concept-ids context concept-ids provider-id)
        bulk-update-params (assoc bulk-update-params :concept-ids concept-ids)
        ;; Write db rows - one for overall status, one for each concept id
        task-id (try
                  (data-bulk-update/create-bulk-update-task
                   context provider-id json concept-ids)
                  (catch Exception e
                    (let [msg (.getMessage e)
                          msg (if (string/includes? msg "BULK_UPDATE_TASK_STATUS_UK")
                                "Bulk update name needs to be unique within the provider."
                                msg)]
                      (errors/throw-service-errors
                       :invalid-data
                       [(str "Error creating bulk update task: " msg)]))))]
    ;; Queue the bulk update event
    (ingest-events/publish-ingest-event
     context
     (ingest-events/collections-bulk-event provider-id task-id bulk-update-params user-id))
    task-id))

(defn handle-bulk-update-event
  "For each concept-id, queueu collection bulk update messages"
  [context provider-id task-id bulk-update-params user-id]
  (let [{:keys [concept-ids]} bulk-update-params]
    (doseq [concept-id concept-ids]
      (ingest-events/publish-ingest-event
       context
       (ingest-events/ingest-collection-bulk-update-event
        provider-id
        task-id
        concept-id
        bulk-update-params
        user-id)))))

(defn- update-collection-concept
  "Perform the update on the collection and update the concept.
   Returns the updated concept or nil if the concept is not found through find-value."
  [context concept bulk-update-params user-id]
  (let [{:keys [update-type update-field find-value update-value]} bulk-update-params
        update-type (csk/->kebab-case-keyword update-type)
        update-field (csk/->PascalCaseKeyword update-field)
        updated-metadata (field-update/update-concept context concept update-type
                                                      [update-field] update-value find-value
                                                      update-format)]
    (when (some? updated-metadata)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format update-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defn- validate-and-save-collection
  "Put concept through ingest validation. Attempt save to
  metadata db. If validation or save errors are thrown, they will be caught and
  handled in handle-collection-bulk-update-event. Return warnings."
  [context concept]
  (let [{:keys [concept warnings]} (ingest-service/validate-and-prepare-collection
                                    context concept {:bulk-update? true})]
    ;; If errors are caught, an error will be thrown and logged to the DB
    ;; If we get warnings back, validation was successful, but will still
    ;; log warnings
    (mdb/save-concept context concept)
    warnings))

(defn- create-success-status-message
  "If there are warnings, create a status string with warnings, otherwise status
  is nil"
  [warnings]
  (when (seq warnings)
    (str "Collection was updated successfully, but translating the collection "
         "to UMM-C had the following issues: "
         (string/join "; " warnings))))

(defn- process-bulk-update-complete
  "Check if the overall bulk update operation is complete and if so, re-index
  provider collections"
  [context provider-id task-id]
  (let [task-status (data-bulk-update/get-bulk-update-task-status-for-provider context task-id provider-id)]
    (when (= complete-status (:status task-status))
      (ingest-events/publish-ingest-event
       context
       (ingest-events/provider-collections-require-reindexing-event
        provider-id
        false)))))

(defn- update-concept-and-status
  "Perform update for the collection concept and collection status."
  [context task-id concept concept-id bulk-update-params user-id]
  (if-let [updated-concept (update-collection-concept context concept bulk-update-params user-id)]
    (let [warnings (validate-and-save-collection context updated-concept)]
      (data-bulk-update/update-bulk-update-task-collection-status
        context task-id concept-id updated-status (create-success-status-message warnings)))
    (data-bulk-update/update-bulk-update-task-collection-status
      context task-id concept-id skipped-status
      (format "Collection with concept-id [%s] is not updated because no find-value found." concept-id))))

(defn handle-collection-bulk-update-event
  "Perform update for the given concept id. Log an error status if the concept-id is not associated
  with the provider-id, or if the concept cannot be found, or if the concept doesn't contain the find-value."
  [context provider-id task-id concept-id bulk-update-params user-id]
  (try
    (if-not (string/ends-with? concept-id provider-id)
      (data-bulk-update/update-bulk-update-task-collection-status
        context task-id concept-id failed-status
        (format "Concept-id [%s] is not associated with provider-id [%s]." concept-id provider-id))
      (if-let [concept (mdb2/get-latest-concept context concept-id)]
        (if (:deleted concept)
          (data-bulk-update/update-bulk-update-task-collection-status
            context task-id concept-id failed-status
            (format "Collection with concept-id [%s] is deleted. Can not be updated." concept-id))
          (update-concept-and-status context task-id concept concept-id bulk-update-params user-id))
        (data-bulk-update/update-bulk-update-task-collection-status
          context task-id concept-id failed-status (format "Concept-id [%s] does not exist." concept-id))))
    (catch clojure.lang.ExceptionInfo ex-info
      (if (= :conflict (:type (.getData ex-info)))
        ;; Concurrent update - re-queue concept update
        (ingest-events/publish-ingest-event
         context
         (ingest-events/ingest-collection-bulk-update-event provider-id
                                                            task-id
                                                            concept-id
                                                            bulk-update-params
                                                            user-id))
        (data-bulk-update/update-bulk-update-task-collection-status
          context task-id concept-id failed-status (.getMessage ex-info))))
    (catch Exception e
      (let [message (or (.getMessage e) default-exception-message)
            concept-id-message (re-find #"Concept-id.*is not valid." message)]
        (if concept-id-message
          (data-bulk-update/update-bulk-update-task-collection-status context task-id concept-id failed-status concept-id-message)
          (data-bulk-update/update-bulk-update-task-collection-status context task-id concept-id failed-status message)))))
  (process-bulk-update-complete context provider-id task-id))
