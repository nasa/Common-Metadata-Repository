(ns cmr.ingest.api.ingest
  "Defines the HTTP URL routes for validating and ingesting concepts."
  (:require
   [clojure.data.xml :as x]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as srvc-errors]
   [cmr.common.util :as util]
   [cmr.common.xml.gen :refer :all]
   [cmr.ingest.services.bulk-update-service :as bulk-update]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.messages :as msg]
   [cmr.ingest.services.providers-cache :as pc]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.echo.tokens :as tokens]
   [compojure.core :refer :all])
  (:import
   (clojure.lang ExceptionInfo)))

(def VALIDATE_KEYWORDS_HEADER "cmr-validate-keywords")
(def ENABLE_UMM_C_VALIDATION_HEADER "cmr-validate-umm-c")

(defn verify-provider-exists
  "Verifies the given provider exists."
  [context provider-id]
  (let [provider (->> context
                      (pc/get-providers-from-cache)
                      (some #(when (= provider-id (:provider-id %)) %)))]
    (when-not provider
      (srvc-errors/throw-service-error
        :invalid-data (format "Provider with provider-id [%s] does not exist." provider-id)))))

(def valid-response-mime-types
  "Supported ingest response formats"
  #{mt/any mt/xml mt/json})

(def content-type-mime-type->response-format
  "A map of mime-types to supported response format"
  {mt/echo10 :xml
   mt/iso19115 :xml
   mt/iso-smap :xml
   mt/dif :xml
   mt/dif10 :xml
   mt/xml :xml
   mt/json :json})

(defn- result-map->xml
  "Converts all keys in a map to tags with values given by the map values to form a trivial
  xml document.
  Example:
  (result-map->xml {:concept-id \"C1-PROV1\", :revision-id 1} true)

  <?xml version=\"1.0\" encoding=\"UTF-8\"?>
  <result>
  <revision-id>1</revision-id>
  <concept-id>C1-PROV1</concept-id>
  </result>"
  [m]
  (x/emit-str
    (x/element :result {}
               (reduce-kv (fn [memo k v]
                            (conj memo (x/element (keyword k) {} v)))
                          []
                          m))))

(defn- get-ingest-result-format
  "Returns the requested ingest result format parsed from the Accept header or :xml
  if no Accept header is given"
  ([headers default-format]
   (get-ingest-result-format
     headers (set (keys content-type-mime-type->response-format)) default-format))
  ([headers valid-mime-types default-format]
   (get content-type-mime-type->response-format
        (mt/extract-header-mime-type valid-response-mime-types headers "accept" true)
        default-format)))

(defn- ingest-status-code
  "Returns the ingest status code when ingest is successful"
  [result]
  (if (= 1 (:revision-id result))
    201
    200))

(defn- contextualize-warnings
  "Add a message to warnings to make translation issues more clear to the user"
  [result]
  (let [warning-context "After translating item to UMM-C the metadata had the following issue: "]
   (update result
          :warnings
          (fn [warnings] (seq (map #(str warning-context %) warnings))))))

(defmulti generate-ingest-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (get-ingest-result-format headers :xml)))

(defmethod generate-ingest-response :json
  [headers result]
  ;; ring-json middleware will handle converting the body to json
  {:status (ingest-status-code result)
   :headers {"Content-Type" (mt/format->mime-type :json)}
   :body result})

(defmethod generate-ingest-response :xml
  [headers result]
  {:status (ingest-status-code result)
   :headers {"Content-Type" (mt/format->mime-type :xml)}
   :body (result-map->xml result)})

(defmulti generate-validate-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (get-ingest-result-format headers :xml)))

(defmethod generate-validate-response :json
  [headers result]
  ;; ring-json middleware will handle converting the body to json
  (if (seq result)
    {:status 200
     :headers {"Content-Type" (mt/format->mime-type :json)}
     :body result}
    {:status 200}))

(defmethod generate-validate-response :xml
  [headers result]
  (if (seq result)
   {:status 200
    :headers {"Content-Type" (mt/format->mime-type :xml)}
    :body (result-map->xml result)}
   {:status 200}))

(defn- invalid-revision-id-error
  "Throw an error saying that revision is invalid"
  [revision-id]
  (srvc-errors/throw-service-error
    :invalid-data
    (msg/invalid-revision-id revision-id)))

(defn- parse-validate-revision-id
  "Parse revision id and return it if it is positive"
  [revision-id]
  (try
    (let [revision-id (Integer/parseInt revision-id)]
      (when (pos? revision-id)
        revision-id))
    (catch NumberFormatException _)))

(defn- set-revision-id
  "Associate revision id to concept if revision id is a positive integer. Otherwise return an error"
  [concept headers]
  (if-let [revision-id (get headers "cmr-revision-id")]
    (if-let [revision-id (parse-validate-revision-id revision-id)]
      (assoc concept :revision-id revision-id)
      (invalid-revision-id-error revision-id))
    concept))

(def user-id-cache-key
  "The cache key for the token user id cache"
  :token-user-ids)

(defn create-user-id-cache
  "Creates cache for user ids associated with tokens"
  []
  (mem-cache/create-in-memory-cache
    :lru
    {}
    {:threshold 1000}))

(defn- set-user-id
  "Associate user id to concept."
  [concept context headers]
  (assoc concept :user-id
         (if-let [user-id (get headers "user-id")]
           user-id
           (when-let [token (get headers transmit-config/token-header)]
             (cache/get-value (cache/context->cache context user-id-cache-key)
                              token
                              (partial tokens/get-user-id context token))))))

(defn- set-concept-id
  "Set concept-id and revision-id for the given concept based on the headers. Ignore the
  revision-id if no concept-id header is passed in."
  [concept headers]
  ;; The header concept-id exists primarily to support backwards compatibility with Catalog Rest
  (if-let [concept-id (or (get headers "cmr-concept-id") (get headers "concept-id"))]
    (assoc concept :concept-id concept-id)
    concept))

(defn- body->concept
  "Create a metadata concept from the given request body"
  [concept-type provider-id native-id body content-type headers]
  (let [metadata (str/trim (slurp body))]
    (-> {:metadata metadata
         :format (mt/keep-version content-type)
         :provider-id provider-id
         :native-id native-id
         :concept-type concept-type}
        (set-concept-id headers)
        (set-revision-id headers))))

(defn- concept->loggable-string
  "Returns a string with information about the concept as a loggable string."
  [concept]
  (pr-str (dissoc concept :metadata)))

(defn set-default-error-format [default-response-format handler]
  "Ring middleware to add a default format to the exception-info created during exceptions. This
  is used to determine the default format for each route."
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (let [{:keys[type errors]} (ex-data e)]
          (throw (ex-info (.getMessage e)
                          {:type type
                           :errors errors
                           :default-format default-response-format})))))))

(defn- get-validation-options
  "Returns a map of validation options with boolean values"
  [headers]
  {:validate-keywords? (= "true" (get headers VALIDATE_KEYWORDS_HEADER))
   :validate-umm? (= "true" (get headers ENABLE_UMM_C_VALIDATION_HEADER))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collection API Functions

(defn validate-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request
        concept (body->concept :collection provider-id native-id body content-type headers)
        validation-options (get-validation-options headers)]
    (verify-provider-exists request-context provider-id)
    (info (format "Validating Collection %s from client %s"
                  (concept->loggable-string concept) (:client-id request-context)))
    (let [validate-response (ingest/validate-and-prepare-collection request-context
                                                                    concept
                                                                    validation-options)]
      (generate-validate-response headers (util/remove-nil-keys (select-keys (contextualize-warnings validate-response) [:warnings]))))))

(defn ingest-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request]
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (body->concept :collection provider-id native-id body content-type headers)
          validation-options (get-validation-options headers)
          save-collection-result (ingest/save-collection
                                  request-context
                                  (set-user-id concept request-context headers)
                                  validation-options)]
      (info (format "Ingesting collection %s from client %s"
              (concept->loggable-string (assoc concept :entry-title (:entry-title save-collection-result)))
              (:client-id request-context)))
      (generate-ingest-response headers (contextualize-warnings
                                          ;; entry-title is added just for the logging above.
                                          ;; dissoc it so that it remains the same as the original code.
                                          (dissoc save-collection-result :entry-title))))))

(defn delete-collection
  [provider-id native-id request]
  (let [{:keys [request-context params headers]} request
        concept-attribs (-> {:provider-id provider-id
                             :native-id native-id
                             :concept-type :collection}
                            (set-revision-id headers)
                            (set-user-id request-context headers))]
    (common-enabled/validate-write-enabled request-context "ingest")
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (info (format "Deleting collection %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (generate-ingest-response headers
                              (contextualize-warnings (ingest/delete-concept
                                                        request-context
                                                        concept-attribs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Granule API Functions

(defmulti validate-granule
  "Validates the granule in the request. It can handle a granule and collection sent as multipart-params
  or a normal request with the XML as the body."
  (fn [provider-id native-id request]
    (if (seq (:multipart-params request))
      :multipart-params
      :default)))

(defmethod validate-granule :default
  [provider-id native-id {:keys [body content-type headers request-context]}]
  (verify-provider-exists request-context provider-id)
  (let [concept (body->concept :granule provider-id native-id body content-type headers)]
    (info (format "Validating granule %s from client %s"
                  (concept->loggable-string concept) (:client-id request-context)))
    (ingest/validate-granule request-context concept)
    {:status 200}))

(defn- multipart-param->concept
  "Converts a multipart parameter "
  [provider-id native-id concept-type {:keys [content-type content]}]
  {:metadata content
   :format (mt/keep-version content-type)
   :provider-id provider-id
   :native-id native-id
   :concept-type concept-type})

(defn validate-multipart-params
  "Validates that the multipart parameters includes only the expected keys."
  [expected-keys-set multipart-params]
  (let [provided-keys (set (keys multipart-params))]
    (when (not= expected-keys-set provided-keys)
      (srvc-errors/throw-service-error
        :bad-request
        (msg/invalid-multipart-params expected-keys-set provided-keys)))))

(defmethod validate-granule :multipart-params
  [provider-id native-id {:keys [multipart-params request-context]}]
  (verify-provider-exists request-context provider-id)
  (validate-multipart-params #{"granule" "collection"} multipart-params)

  (let [coll-concept (multipart-param->concept
                       provider-id native-id :collection (get multipart-params "collection"))
        gran-concept (multipart-param->concept
                       provider-id native-id :granule (get multipart-params "granule"))]
    (ingest/validate-granule-with-parent-collection request-context gran-concept coll-concept)
    {:status 200}))

(defn ingest-granule
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request]
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (body->concept :granule provider-id native-id body content-type headers)]
      (info (format "Ingesting granule %s from client %s"
                    (concept->loggable-string concept) (:client-id request-context)))
      (generate-ingest-response headers (ingest/save-granule request-context concept)))))

(defn delete-granule
  [provider-id native-id request]
  (let [{:keys [request-context params headers]} request
        concept-attribs (set-revision-id
                          {:provider-id provider-id
                           :native-id native-id
                           :concept-type :granule}
                          headers)]

    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (info (format "Deleting granule %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (generate-ingest-response headers (ingest/delete-concept request-context concept-attribs))))

(defn- bulk-update-collections
  "Bulk update collections. Validate provider exists, check ACLs, and validate
  POST body.
  Will update as more functionality is added"
  [provider-id request]
  (let [{:keys [body headers request-context]} request
        body (str/trim (slurp body))]
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (bulk-update/validate-and-queue-bulk-update request-context provider-id body)
    (generate-ingest-response
      headers
      {:status 200
       :task-id "ABCDEF123"}))) ; hardcoded for now

(defn- generate-xml-status-list
 "Generate XML for a status list with the format
 {:id :status :status-message}"
 [result status-list-key status-key id-key]
 (x/element status-list-key {}
   (for [status (get result status-list-key)
         :let [message (:status-message status)]]
    (x/element status-key {}
     (x/element id-key {} (get status id-key))
     (x/element :status {} (:status status))
     (when message
      (x/element :status-message {} message))))))

(defmulti generate-provider-tasks-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (get-ingest-result-format headers :xml)))

(defmethod generate-provider-tasks-response :json
  [headers result]
  ;; No special processing needed
  (generate-ingest-response headers result))

(defmethod generate-provider-tasks-response :xml
  [headers result]
  ;; Create an xml response for a list of tasks
  {:status (ingest-status-code result)
   :headers {"Content-Type" (mt/format->mime-type :xml)}
   :body (x/emit-str
          (x/element :result {}
           (generate-xml-status-list result :tasks :task :task-id)))})

(defn- get-provider-tasks
 "Get all tasks and statuses for provider - hardcoded for now"
 [provider-id request]
 (let [{:keys [headers request-context]} request]
  (verify-provider-exists request-context provider-id)
  (acl/verify-ingest-management-permission request-context :read :provider-object provider-id)
  (generate-provider-tasks-response
   headers
   {:status 200
    :tasks [{:task-id "ABCDEF123"
             :status "IN_PROGRESS"}
            {:task-id "12345678"
             :status "COMPLETE"}
            {:task-id "XYZ123456"
             :status "COMPLETE"}]})))

(defmulti generate-provider-task-status-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (get-ingest-result-format headers :xml)))

(defmethod generate-provider-task-status-response :json
  [headers result]
  ;; No special processing needed
  (generate-ingest-response headers result))

(defmethod generate-provider-task-status-response :xml
  [headers result]
  ;; Create an xml response for a list of tasks
  {:status (ingest-status-code result)
   :headers {"Content-Type" (mt/format->mime-type :xml)}
   :body (x/emit-str
          (x/element :result {}
           (x/element :task-status {} (:task-status result))
           (x/element :status-message {} (:status-message result))
           (generate-xml-status-list result
            :collection-statuses :collection-status :concept-id)))})

(defn- get-provider-task-status
 "Get the status for the given task for the provider - hardcoded for now"
 [provider-id task-id request]
 (let [{:keys [headers request-context]} request]
  (verify-provider-exists request-context provider-id)
  (acl/verify-ingest-management-permission request-context :read :provider-object provider-id)
  (generate-provider-task-status-response
   headers
   {:status 200
    :task-status "COMPLETE"
    :status-message "The bulk update completed with 2 errors"
    :collection-statuses [{:concept-id "C1-PROV"
                           :status-message "Missing required properties"}
                          {:concept-id "C2-PROV"
                           :status-message "Invalid XML"}]})))

(def ingest-routes
  "Defines the routes for ingest, validate, and delete operations"
  (set-default-error-format
    :xml
    (context "/providers/:provider-id" [provider-id]

      (context ["/validate/collection/:native-id" :native-id #".*$"] [native-id]
        (POST "/" request
          (validate-collection provider-id native-id request)))
      (context ["/collections/:native-id" :native-id #".*$"] [native-id]
        (PUT "/" request
          (ingest-collection provider-id native-id request))
        (DELETE "/" request
          (delete-collection provider-id native-id request)))

      (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
        (POST "/" request
          (validate-granule provider-id native-id request)))

      (context ["/granules/:native-id" :native-id #".*$"] [native-id]
        (PUT "/" request
          (ingest-granule provider-id native-id request))
        (DELETE "/" request
          (delete-granule provider-id native-id request)))

      (context "/bulk-update" []
        (context "/collections" []
          (POST "/" request
           (let [request-context (:request-context request)]
             (bulk-update-collections provider-id request)))
          (GET "/status" request ; Gets all tasks for provider
            (let [request-context (:request-context request)]
              (get-provider-tasks provider-id request)))
          (context "/status/:task-id" [task-id]
            (GET "/" request
              (let [request-context (:request-context request)]
                (get-provider-task-status provider-id task-id request)))))))))
