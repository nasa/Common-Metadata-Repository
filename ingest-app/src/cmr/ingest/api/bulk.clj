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
   [cmr.ingest.services.bulk-update-service :as bulk-update]
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
      (let [_task-id (bulk-update/validate-and-save-bulk-granule-update
                      request-context
                      provider-id
                      content
                      user-id)]
        (api-core/generate-ingest-response
         ;; 501 => not-yet-implemented
         headers
         {:status 501
          :message (str "The bulk granule update feature has not been completed yet. "
                        "Please refer to "
                        "https://cmr.earthdata.nasa.gov/search/site/docs/ingest/api.html "
                        "or contact cmr@nasa.gov for information regarding bulk granule update.")})))))

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

(defn get-provider-tasks
  "Get all tasks and task statuses for provider."
  [provider-id request]
  (let [{:keys [headers request-context]} request]
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :read :provider-object provider-id)
    (generate-provider-tasks-response
     headers
     {:status 200
      :tasks (data-bulk-update/get-bulk-update-statuses-for-provider request-context provider-id)})))

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

(defn get-provider-task-status
  "Get the status for the given task for the provider including collection statuses"
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
