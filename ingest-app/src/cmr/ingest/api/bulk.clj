(ns cmr.ingest.api.bulk
  "Bulk ingest functions in support of the ingest API."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as srvc-errors]
   [cmr.common.xml.gen :refer :all]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.config :as ingest-config]
   [cmr.ingest.data.bulk-update :as data-bulk-update]
   [cmr.ingest.data.granule-bulk-update :as data-gran-bulk-update]
   [cmr.ingest.services.bulk-update-service :as bulk-update]
   [cmr.ingest.services.granule-bulk-update-service :as gran-bulk-update]
   [cmr.ingest.services.ingest-service :as ingest]))

(defn bulk-update-collections
  "Bulk update collections. Validate provider exists, check ACLs, and validate
  POST body. Writes rows to tables and returns task id"
  [provider-id request]
  (if-not (ingest-config/collection-bulk-update-enabled)
    (srvc-errors/throw-service-error
     :bad-request "Bulk update is disabled.")
    (let [{:keys [body headers request-context]} request
          content (api-core/read-body! body)
          user-id (api-core/get-user-id request-context headers)]
      (lt-validation/validate-launchpad-token request-context)
      (api-core/verify-provider-exists request-context provider-id)
      (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
      (let [task-id (bulk-update/validate-and-save-bulk-update
                     request-context
                     provider-id
                     content
                     user-id)]
        (api-core/generate-ingest-response
         headers
         {:status 200
          :task-id task-id})))))

(defn bulk-update-granules
  "Bulk update granules. Validate provider exists, check ACLs, and validate
   POST body. Writes tasks to queue for worker to begin processing."
  [provider-id request]
  (if-not (ingest-config/granule-bulk-update-enabled)
    (srvc-errors/throw-service-error :bad-request "Bulk granule update is disabled")
    (let [{:keys [body headers request-context]} request
          content (api-core/read-body! body)
          user-id (api-core/get-user-id request-context headers)]
      (lt-validation/validate-launchpad-token request-context)
      (api-core/verify-provider-exists request-context provider-id)
      (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
      (let [task-id (gran-bulk-update/validate-and-save-bulk-granule-update
                      request-context
                      provider-id
                      content
                      user-id)]
        (api-core/generate-ingest-response
         headers
         {:status 200
          :task-id task-id})))))

(defn- generate-xml-status-list
  "Generate XML for a status list with the format
  {:id :status :status-message}"
  [result status-list-key status-key id-key]
  (xml/element status-list-key {}
               (for [status (get result status-list-key)
                     :let [message (:status-message status)]]
                 (xml/element status-key {}
                              (xml/element id-key {} (get status id-key))
                              (xml/element :status {} (:status status))
                              (xml/element :status-message {} message)))))

(defn- generate-xml-provider-tasks-list
 "Generate XML for a status list with the format
 {:id :status :status-message}"
 [result status-list-key status-key id-key name-key created-at-key additional-keys]
 (xml/element status-list-key {}
   (for [status (get result status-list-key)
         :let [message (:status-message status)]]
    (xml/element status-key {}
     (xml/element created-at-key {} (str (:created-at status)))
     (xml/element name-key {} (str (:name status)))
     (xml/element id-key {} (get status id-key))
     (xml/element :status {} (:status status))
     (xml/element :status-message {} message)
     (for [k additional-keys]
      (xml/element k {} (get status k)))))))

(defmulti generate-provider-tasks-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (api-core/get-ingest-result-format headers :xml)))

(defmethod generate-provider-tasks-response :json
  [headers result]
  ;; No special processing needed
  (api-core/generate-ingest-response headers result))

(defmethod generate-provider-tasks-response :xml
  [headers result]
  ;; Create an xml response for a list of tasks
  {:status (api-core/ingest-status-code result)
   :headers {"Content-Type" (mt/format->mime-type :xml)}
   :body (xml/emit-str
          (xml/element :result {}
                       (generate-xml-provider-tasks-list result :tasks :task
                                                         :task-id :name :created-at
                                                         [:request-json-body])))})

(defmulti get-provider-tasks*
  "Get bulk update tasks status based on concept type"
  (fn [context provider-id concept-type]
    concept-type))

(defmethod get-provider-tasks* :collection
  [context provider-id _]
  (data-bulk-update/get-collection-tasks context provider-id))

(defmethod get-provider-tasks* :granule
  [context provider-id _]
  (data-gran-bulk-update/get-granule-tasks-by-provider context provider-id))

(defn get-provider-tasks
  "Get all tasks and task statuses for provider."
  [concept-type provider-id request]
  (let [{:keys [headers request-context]} request]
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :read :provider-object provider-id)
    (generate-provider-tasks-response
     headers
     {:status 200
      :tasks (get-provider-tasks* request-context provider-id concept-type)})))

(defmulti generate-provider-task-status-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (api-core/get-ingest-result-format headers :xml)))

(defmethod generate-provider-task-status-response :json
  [headers result]
  ;; No special processing needed
  (api-core/generate-ingest-response headers result))

(defmethod generate-provider-task-status-response :xml
  [headers result]
  ;; Create an xml response for a list of tasks
  {:status (api-core/ingest-status-code result)
   :headers {"Content-Type" (mt/format->mime-type :xml)}
   :body (xml/emit-str
          (xml/element :result {}
           (xml/element :created-at {} (str (:created-at result)))
           (xml/element :name {} (str (:name result)))
           (xml/element :task-status {} (:task-status result))
           (xml/element :status-message {} (:status-message result))
           (xml/element :request-json-body {} (:request-json-body result))
           (generate-xml-status-list result
            :collection-statuses :collection-status :concept-id)))})

(defn- validate-granule-bulk-update-result-format
  [headers]
  (let [result-format (api-core/get-ingest-result-format headers :json)]
    (when-not (= :json result-format)
      (srvc-errors/throw-service-error
       :bad-request "Granule bulk update task status is only supported in JSON format."))))

(defn get-collection-task-status
  "Get the status for the given task including collection statuses"
  [provider-id task-id request]
  (let [{:keys [headers request-context]} request]
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :read :provider-object provider-id)
    (let [task-status (data-bulk-update/get-bulk-update-task-status-for-provider request-context task-id provider-id)
          collection-statuses (data-bulk-update/get-bulk-update-collection-statuses-for-task request-context task-id)]
      (when (or (nil? task-status) (nil? (:status task-status)))
        (srvc-errors/throw-service-error
          :not-found (format "Bulk update task with task id [%s] could not be found for provider id [%s]." task-id provider-id)))
      (generate-provider-task-status-response
       headers
       {:status 200
        :created-at (:created-at task-status)
        :name (:name task-status)
        :task-status (:status task-status)
        :status-message (:status-message task-status)
        :request-json-body (:request-json-body task-status)
        :collection-statuses collection-statuses}))))

(defn- generate-status-progress-message
  "Generate progress message for an in-progress bulk granule update"
  [granule-statuses]
  (let [gran-count (count granule-statuses)
        pending-count (count (filter #(= (:status %) "PENDING") granule-statuses))]
    (if (zero? pending-count)
     (format "Complete.")
     (format "Of %d total granules, %d granules have been processed and %d are still pending."
      gran-count (- gran-count pending-count) pending-count))))


(defn- get-granule-task-status-response-generator
  "Generates the response for a bulk granule update task status request. Depending on the parameters
   show_request, show_progress, and show_granules, the response will be more or less verbose.
   show_progress and show_granules require a full query of granules in the request, which can cause
   this response generation to be much more expensive."
  [request-context task-id gran-task params]
  (let [{:keys [name created-at status status-message request-json-body]} gran-task
        {:keys [show_request show_progress show_granules]} params
        ;this call makes the status response much more expensive to retrieve
        granule-statuses (when (or (= "true" show_progress) (= "true" show_granules))
                           (data-gran-bulk-update/get-bulk-update-granule-statuses-for-task
                            request-context task-id))
        response-fields {:status 200
                         :created-at created-at
                         :name name
                         :task-status status
                         :status-message status-message}
        extra-fields (as-> {} intermediate
                                (if (= "true" show_progress)
                                  (assoc intermediate :progress (generate-status-progress-message
                                                                 granule-statuses))
                                  intermediate)
                                (if (= "true" show_request)
                                  (assoc intermediate :request-json-body request-json-body)
                                  intermediate)
                                (if (= "true" show_granules)
                                  (assoc intermediate :granule-statuses granule-statuses)
                                  intermediate))]
     (conj response-fields extra-fields)))

(defn get-granule-task-status
  "Get the status for the given bulk granule update task"
  [request task-id]
  (let [{:keys [headers request-context params]} request
        _ (validate-granule-bulk-update-result-format headers)
        gran-task (data-gran-bulk-update/get-granule-task-by-id request-context task-id)
        provider-id (:provider-id gran-task)]
    (when-not provider-id
      (srvc-errors/throw-service-error
       :not-found
       (format "Granule bulk update task with task id [%s] could not be found." task-id)))

    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :read :provider-object provider-id)

    (api-core/generate-ingest-response
     (assoc headers "accept" mt/json)
     (get-granule-task-status-response-generator request-context task-id gran-task params))))

(defn update-completed-granule-task-statuses
  "On demand capability to update granule task statuses. Marks bulk granule
   update tasks as complete when there are no granules marked as PENDING."
  [request]
  (let [{:keys [request-context]} request]
    (acl/verify-ingest-management-permission request-context)
    (gran-bulk-update/update-completed-task-status! request-context)))
