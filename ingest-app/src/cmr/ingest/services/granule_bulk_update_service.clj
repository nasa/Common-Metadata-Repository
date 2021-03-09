(ns cmr.ingest.services.granule-bulk-update-service
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.data.granule-bulk-update :as data-granule-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.services.bulk-update-service :as bulk-update-service]
   [cmr.ingest.services.granule-bulk-update.echo10 :as echo10]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.transmit.metadata-db :as mdb]))

(def granule-bulk-update-schema
 (js/json-string->json-schema (slurp (io/resource "granule_bulk_update_schema.json"))))

(defn- validate-granule-bulk-update
 [json]
 (js/validate-json! granule-bulk-update-schema json))

(defn- update->instruction
  "Returns the granule bulk update instruction for a single update item"
  [event-type item]
  (let [[granule-ur url] item]
    {:event-type event-type
     :granule-ur granule-ur
     :url url}))

(defn- request->instructions
  "Returns granule bulk update instructions for the given request"
  [parsed-json]
  (let [{:keys [operation update-field updates]} parsed-json
        event-type (str operation ":" update-field)]
    (map (partial update->instruction event-type) updates)))

(defn validate-and-save-bulk-granule-update
  "Validate the granule bulk update request, save rows to the db for task
  and granule statuses, and queue bulk granule update. Return task id, which comes
  from the db save."
  [context provider-id json-body user-id]
  (validate-granule-bulk-update json-body)
  (let [instructions (-> json-body
                         (json/parse-string true)
                         request->instructions)
        task-id (try
                  (data-granule-bulk-update/create-granule-bulk-update-task
                   context
                   provider-id
                   user-id
                   json-body
                   instructions)
                  (catch Exception e
                    (error "validate-and-save-bulk-granule-update caught exception:" e)
                    (let [message (.getMessage e)
                          user-facing-message (if (string/includes? message "GBUT_PN_I")
                                                "Granule bulk update task name needs to be unique within the provider."
                                                message)]
                      (errors/throw-service-errors
                       :invalid-data
                       [(str "error creating granule bulk update task: " user-facing-message)]))))]
    ;; Queue the granules bulk update event
    (ingest-events/publish-gran-bulk-update-event
     context
     (ingest-events/granules-bulk-event provider-id task-id user-id instructions))
    task-id))

(defn handle-granules-bulk-event
  "For each granule-ur, queue a granule bulk update message"
  [context provider-id task-id bulk-update-params user-id]
  (doseq [instruction bulk-update-params]
    (ingest-events/publish-gran-bulk-update-event
     context
     (ingest-events/ingest-granule-bulk-update-event provider-id task-id user-id instruction))))

(defmulti add-opendap-url
  "Add OPeNDAP url to the given granule concept."
  (fn [context concept url]
    (mt/format-key (:format concept))))

(defmethod add-opendap-url :echo10
  [context concept url]
  (echo10/add-opendap-url (:metadata concept) url))

(defmethod add-opendap-url :default
  [context concept url]
  (errors/throw-service-errors
   :invalid-data (format "Add OPeNDAP url is not supported for format [%s]" (:format concept))))

(defmulti update-granule-concept
  "Perform the update of the granule concept."
  (fn [context concept bulk-update-params user-id]
    (keyword (:event-type bulk-update-params))))

(defmethod update-granule-concept :UPDATE_FIELD:OPeNDAPLink
  [context concept bulk-update-params user-id]
  (let [{:keys [format metadata]} concept
        {:keys [granule-ur url]} bulk-update-params
        updated-metadata (add-opendap-url context concept url)]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defmethod update-granule-concept :default
[context concept bulk-update-params user-id]
)

(defn- update-granule-concept-and-status
  "Perform update for the granule concept and granule bulk update status."
  [context task-id concept granule-ur bulk-update-params user-id]
  (if-let [updated-concept (update-granule-concept context concept bulk-update-params user-id)]
    (do
      (ingest-service/save-granule context updated-concept)
      (data-granule-bulk-update/update-bulk-update-task-granule-status
       context task-id granule-ur bulk-update-service/updated-status ""))
    (data-granule-bulk-update/update-bulk-update-task-granule-status
     context task-id granule-ur bulk-update-service/skipped-status
     (format (str "Granule with granule-ur [%s] in task-id [%s] is not updated "
                  "because the metadata format [%s] is not supported.")
             granule-ur task-id (:format concept)))))

(defn handle-granule-bulk-update-event
  [context provider-id task-id bulk-update-params user-id]
  (let [{:keys [granule-ur]} bulk-update-params]
    (try
      (if-let [concept (mdb/find-latest-concept
                        context {:provider-id provider-id :granule-ur granule-ur} :granule)]
        (if (:deleted concept)
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status
           (format (str "Granule with granule-ur [%s] on provider [%s] in task-id [%s] "
                        "is deleted. Can not be updated.")
                   granule-ur provider-id task-id))
          ;; granule found and not deleted, update the granule
          (update-granule-concept-and-status
           context task-id concept granule-ur bulk-update-params user-id))
        ;; granule not found
        (data-granule-bulk-update/update-bulk-update-task-granule-status
         context task-id granule-ur bulk-update-service/failed-status
         (format "Granule UR [%s] in task-id [%s] does not exist." granule-ur task-id)))
      (catch clojure.lang.ExceptionInfo ex-info
        (error "handle-granule-bulk-update-event caught ExceptionInfo:" ex-info)
        (if (= :conflict (:type (.getData ex-info)))
          ;; Concurrent update - re-queue concept update
          (ingest-events/publish-ingest-event
           context
           (ingest-events/ingest-granule-bulk-update-event
            provider-id task-id user-id bulk-update-params))
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status (.getMessage ex-info))))
      (catch Exception e
        (error "handle-granule-bulk-update-event caught exception:" e)
        (let [message (or (.getMessage e) bulk-update-service/default-exception-message)]
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status message))))))
