(ns cmr.system-int-test.utils.ingest-util
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.data :as d]
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.acl.core :as acl]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.ingest.config :as ingest-config]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.provider-holdings :as ph]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.versioning :as umm-versioning]
   [cmr.umm.echo10.echo10-collection :as c]
   [cmr.umm.echo10.echo10-core :as echo10]
   [cmr.umm.echo10.granule :as g])
  (:import
   (java.lang NumberFormatException)))

(defn assert-user-id
  "Assert concept with the given concept-id and revision-id in metadata db has
  user id equal to expected-user-id"
  [concept-id revision-id expected-user-id]
  (is (= expected-user-id (:user-id (mdb/get-concept concept-id revision-id)))))

(defn disable-ingest-writes
  "Use the enable/disable endpoint on ingest to disable writes."
  []
  (let [response (client/post (url/disable-ingest-writes-url))]
    (is (= 200 (:status response)))))

(defn enable-ingest-writes
  "Use the enable/disable endpoint on ingest to enable writes."
  []
  (let [response (client/post (url/enable-ingest-writes-url))]
    (is (= 200 (:status response)))))

(defn- create-provider-through-url
  "Create the provider by http POST on the given url"
  [provider endpoint-url]
  (client/post endpoint-url
               {:body (json/generate-string provider)
                :content-type :json
                :throw-exceptions false
                :connection-manager (s/conn-mgr)
                :headers {transmit-config/token-header (transmit-config/echo-system-token)}}))

(defn create-mdb-provider
  "Create the provider with the given provider id in the metadata db"
  [provider]
  (create-provider-through-url provider (url/create-provider-url)))

(defn create-ingest-provider
  "Create the provider with the given provider id through ingest app"
  [provider]
  (create-provider-through-url provider (url/ingest-create-provider-url)))

(defn get-providers-through-url
  [provider-url]
  (-> (client/get provider-url {:connection-manager (s/conn-mgr)})
      :body
      (json/decode true)))

(defn get-providers
  []
  (get-providers-through-url (url/create-provider-url)))

(defn get-ingest-providers
  []
  (get-providers-through-url (url/ingest-create-provider-url)))

(defn delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (let [{:keys [status]} (client/delete
                           (url/delete-provider-url provider-id)
                           {:throw-exceptions false
                            :connection-manager (s/conn-mgr)
                            :headers {transmit-config/token-header (transmit-config/echo-system-token)}})]
    (is (contains? #{204 404} status))))

(defn delete-ingest-provider
  "Delete the provider with the matching provider-id through the CMR ingest app."
  ([provider-id]
   (delete-ingest-provider provider-id nil))
  ([provider-id headers]
   (let [{:keys [status body] :as response}
         (client/delete (url/ingest-provider-url provider-id)
                        {:throw-exceptions false
                         :connection-manager (s/conn-mgr)
                         :headers (merge {transmit-config/token-header (transmit-config/echo-system-token)}
                                         headers)})
         errors (:errors (json/decode body true))
         content-type (get-in response [:headers :content-type])
         content-length (get-in response [:headers :content-length])]
     {:status status
      :errors errors
      :content-type content-type
      :content-length content-length})))

(defn update-ingest-provider
  "Updates the ingest provider with the given parameters, which is a map of key and value for
  provider-id, short-name, cmr-only and small fields of the provider."
  [params]
  (client/put (url/ingest-provider-url (:provider-id params))
              {:throw-exceptions false
               :body (json/generate-string params)
               :content-type :json
               :connection-manager (s/conn-mgr)
               :headers {transmit-config/token-header (transmit-config/echo-system-token)}}))

(defn reindex-collection-permitted-groups
  "Tells ingest to run the reindex-collection-permitted-groups job"
  ([]
   (reindex-collection-permitted-groups nil))
  ([token]
   (let [response (client/post (url/reindex-collection-permitted-groups-url)
                               {:connection-manager (s/conn-mgr)
                                :query-params {:token token}})]
     (is (= 200 (:status response))))))

(defn reindex-all-collections
  "Tells ingest to run the reindex all collections job"
  ([]
   (reindex-all-collections nil))
  ([{:keys [force-version]}]
   (let [response (client/post (url/reindex-all-collections-url)
                               {:connection-manager (s/conn-mgr)
                                :query-params {:force_version force-version}})]
     (is (= 200 (:status response))))))

(defn cleanup-expired-collections
  "Tells ingest to run the cleanup-expired-collections job"
  []
  (let [response (client/post (url/cleanup-expired-collections-url)
                              {:connection-manager (s/conn-mgr)})]
    (is (= 200 (:status response)))))

(defn cleanup-bulk-granule-update-tasks
  "Tells ingest to run the trigger-bulk-granule-task-cleanup job"
  []
  (let [response (client/post (url/cleanup-granule-bulk-update-task-url)
                              {:connection-manager (s/conn-mgr)})]
    (is (= 200 (:status response)))))

(defn translate-metadata
  "Translates metadata using the ingest translation endpoint. Returns the response."
  ([concept-type input-format metadata output-format options]
   (client/post (url/translate-metadata-url concept-type)
                {:connection-manager (s/conn-mgr)
                 :throw-exceptions false
                 :body metadata
                 :query-params (:query-params options)
                 :headers {"content-type" (mt/format->mime-type input-format)
                           "accept" (mt/format->mime-type output-format)
                           "cmr-skip-sanitize-umm-c" (:skip-sanitize-umm-c options)}}))
  ([concept-type input-format metadata output-format]
   (translate-metadata concept-type input-format metadata output-format nil)))

(defn translate-between-umm-versions
  "Translates two umm-versions using the ingest translation endpoint. Returns the response."
  ([concept-type input-version metadata output-version options]
   (let [format-base "application/vnd.nasa.cmr.umm+json;version="]
    (client/post (url/translate-metadata-url concept-type)
                 {:connection-manager (s/conn-mgr)
                  :throw-exceptions false
                  :body metadata
                  :query-params (:query-params options)
                  :headers {"Content-Type" (str format-base input-version)
                            "Accept" (str format-base output-version)}})))
  ([concept-type input-format metadata output-format]
   (translate-between-umm-versions concept-type input-format metadata output-format nil)))

(defn- parse-error-path
  "Convert the error path string into a sequence with element conversion to integers where possible"
  [path]
  (when path
    (map (fn [^String v]
           (try
             (Integer. v)
             (catch NumberFormatException _
               v)))
         (str/split path #"/"))))

(defn- parse-xml-error-elem
  "Parse an xml error entry. If this contains a path then we need to return map with a :path
  and an :errors tag. Otherwise, just return the list of error messages."
  [elem]
  (if-let [path (parse-error-path (cx/string-at-path elem [:path]))]
    {:errors (vec (cx/strings-at-path elem [:errors :error])) :path path}
    (first (:content elem))))

(defn- parse-xml-error-response-elem
  "Parse an xml error response element"
  [elem]
  (let [{:keys [tag content]} elem
        expanded-content (map parse-xml-error-response-elem content)]
    (if (= :error tag)
      (parse-xml-error-elem elem)
      {tag expanded-content})))

(defmulti parse-ingest-body
  "Parse the ingest response body as a given format"
  (fn [response-format body]
    response-format))

(defmethod parse-ingest-body :xml
  [response-format response]
  (try
    (let [xml-elem (x/parse-str (:body response))]
      (if-let [errors (seq (cx/strings-at-path xml-elem [:error]))]
        (parse-xml-error-response-elem xml-elem)
        (util/remove-nil-keys
         {:concept-id (cx/string-at-path xml-elem [:concept-id])
          :native-id (cx/string-at-path xml-elem [:native-id])
          :revision-id (Integer. (cx/string-at-path xml-elem [:revision-id]))
          :warnings (cx/string-at-path xml-elem [:warnings])
          :existing-errors (cx/string-at-path xml-elem [:existing-errors])
          :body (:body response)})))

    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response)) e))))))

(defmethod parse-ingest-body :json
  [response-format response]
  (try
    (assoc (json/decode (:body response) true) :body (:body response))
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response))) e)))))

(defn parse-ingest-response
  "Parse an ingest response (if required) and append a status"
  [response options]
  (if (get options :raw? false)
    response
    (let [response-format (or (:accept-format options)
                              (if-let [header-format (get-in response [:headers "Content-Type"])]
                                (mt/mime-type->format header-format)
                                :xml))]
      (assoc (parse-ingest-body response-format response)
             :status (:status response)))))

(defmulti parse-validate-body
  "Parse the validate response body as a given format"
  (fn [response-format body]
    response-format))

(defmethod parse-validate-body :xml
  [response-format response]
  (when (not-empty (:body response))
    (try
      (let [xml-elem (x/parse-str (:body response))]
        (if-let [errors (seq (cx/strings-at-path xml-elem [:error]))]
          (parse-xml-error-response-elem xml-elem)
          {:warnings (cx/string-at-path xml-elem [:warnings])}))

      (catch Exception e
        (throw (Exception. (str "Error parsing validate-format body: " (pr-str (:body response)) e)))))))

(defmethod parse-validate-body :json
  [response-format response]
  (try
    (json/decode (:body response) true)
    (catch Exception e
      (throw (Exception. (str "Error parsing validate body: " (pr-str (:body response))) e)))))

(defn parse-validate-response
  "Parse a validate response (if required) and append a status"
  [response options]
  (if (get options :raw? false)
    response
    (assoc (parse-validate-body (or (:accept-format options) :xml) response)
           :status (:status response))))

(defn exec-ingest-http-request
  "Execute the http request defined by the given params map and returns the parsed ingest response."
  [params]
  (parse-ingest-response
    (client/request (assoc params :throw-exceptions false :connection-manager (s/conn-mgr))) {}))

(defn parse-map-response
  "Parse the response as a Clojure map, optionally providing a data validation
  function."
  ([response]
   (parse-map-response response identity))
  ([{:keys [status body]} data-validator]
   (let [body (util/kebab-case-data body data-validator)]
     (if (map? body)
       (assoc body :status status)
       {:status status
        :body body}))))

(defmulti assert-convert-kebab-case
  "For use in asserting that the field names in the map returned by the ingest
  app do not have dashes."
  (fn [_ x] (type x)))

(defmethod assert-convert-kebab-case clojure.lang.IPersistentMap
  [check-keys concept-map]
  (->> check-keys
       (select-keys concept-map)
       (empty?)
       (assert)))

(defmethod assert-convert-kebab-case clojure.lang.Sequential
  [check-keys concept-maps]
  (map (partial assert-convert-kebab-case check-keys) concept-maps))

(defmethod assert-convert-kebab-case java.lang.String
  [check-keys concept-map]
  (assert-convert-kebab-case check-keys (json/parse-string concept-map)))

(defn concept
  "Returns the concept map for ingest"
  [concept-type provider-id native-id format-key metadata]
  (let [mime-type (mt/format->mime-type format-key)]
    {:concept-type concept-type
     :provider-id provider-id
     :native-id native-id
     :metadata metadata
     :format mime-type}))

(defn ingest-variable
  "Ingest a variable using the new association endpoint."
  ([variable]
   (ingest-variable variable {}))
  ([variable options]
   (let [{:keys [metadata format concept-id revision-id native-id coll-concept-id coll-revision-id]} variable
         {:keys [token client-id user-id validate-keywords validate-umm-c cmr-request-id x-request-id]} options
         accept-format (:accept-format options)
         headers (util/remove-nil-keys {"Cmr-Concept-id" concept-id
                                        "Cmr-Revision-id" revision-id
                                        "Cmr-Validate-Keywords" validate-keywords
                                        "Cmr-Validate-Umm-C" validate-umm-c
                                        "Authorization" token
                                        "User-Id" user-id
                                        "Client-Id" client-id
                                        "CMR-Request-Id" cmr-request-id
                                        "X-Request-Id" x-request-id})
         params {:method :put
                 :url (url/ingest-variable-url coll-concept-id coll-revision-id native-id)
                 :body  metadata
                 :content-type format
                 :headers headers
                 :throw-exceptions false
                 :connection-manager (s/conn-mgr)}
         params (merge params (when accept-format {:accept accept-format}))]
     (parse-ingest-response (client/request params) options))))

(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  ([concept]
   (ingest-concept concept {}))
  ([concept options]
   (let [{:keys [metadata format concept-type concept-id revision-id provider-id native-id]} concept
         {:keys [token client-id user-id validate-keywords validate-umm-c cmr-request-id x-request-id test-existing-errors]} options
         accept-format (:accept-format options)
         method (get options :method :put)
         headers (util/remove-nil-keys {"Cmr-Concept-id" concept-id
                                        "Cmr-Revision-id" revision-id
                                        "Cmr-Validate-Keywords" validate-keywords
                                        "Cmr-Validate-Umm-C" validate-umm-c
                                        "Cmr-Test-Existing-Errors" test-existing-errors
                                        "Authorization" token
                                        "User-Id" user-id
                                        "Client-Id" client-id
                                        "CMR-Request-Id" cmr-request-id
                                        "X-Request-Id" x-request-id})
         params {:method method
                 :url (url/ingest-url provider-id concept-type native-id)
                 :body  metadata
                 :content-type format
                 :headers headers
                 :throw-exceptions false
                 :connection-manager (s/conn-mgr)}
         params (merge params (when accept-format {:accept accept-format}))]
     (parse-ingest-response (client/request params) options))))

;; Temporary function, this calls the subscription routes under the ingest root url, will be removed in CMR-8270
(defn ingest-subscription-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  ([concept]
   (ingest-subscription-concept concept {}))
  ([concept options]
   (let [{:keys [metadata format concept-type concept-id revision-id native-id]} concept
         {:keys [token client-id user-id validate-keywords validate-umm-c cmr-request-id x-request-id test-existing-errors]} options
         accept-format (:accept-format options)
         method (get options :method :put)
         headers (util/remove-nil-keys {"Cmr-Concept-id" concept-id
                                        "Cmr-Revision-id" revision-id
                                        "Cmr-Validate-Keywords" validate-keywords
                                        "Cmr-Validate-Umm-C" validate-umm-c
                                        "Cmr-Test-Existing-Errors" test-existing-errors
                                        "Authorization" token
                                        "User-Id" user-id
                                        "Client-Id" client-id
                                        "CMR-Request-Id" cmr-request-id
                                        "X-Request-Id" x-request-id})
         params {:method method
                 :url (url/ingest-subscription-url native-id)
                 :body  metadata
                 :content-type format
                 :headers headers
                 :throw-exceptions false
                 :connection-manager (s/conn-mgr)}
         params (merge params (when accept-format {:accept accept-format}))]
     (parse-ingest-response (client/request params) options))))

(defn delete-subscription-concept
  "Delete a given concept."
  ([concept]
   (delete-subscription-concept concept {}))
  ([concept options]
   (let [{:keys [concept-type native-id]} concept
         {:keys [token client-id accept-format revision-id user-id]} options
         headers (util/remove-nil-keys {"Authorization" token
                                        "Client-Id" client-id
                                        "User-Id" user-id
                                        "Cmr-Revision-id" revision-id})
         params {:method :delete
                 :url (url/ingest-subscription-url native-id)
                 :headers headers
                 :accept accept-format
                 :throw-exceptions false
                 :connection-manager (s/conn-mgr)}
         params (merge params (when accept-format {:accept accept-format}))]
     (parse-ingest-response (client/request params) options))))

(defn delete-concept
  "Delete a given concept."
  ([concept]
   (delete-concept concept {}))
  ([concept options]
   (let [{:keys [provider-id concept-type native-id]} concept
         {:keys [token client-id accept-format revision-id user-id]} options
         headers (util/remove-nil-keys {"Authorization" token
                                        "Client-Id" client-id
                                        "User-Id" user-id
                                        "Cmr-Revision-id" revision-id})
         params {:method :delete
                 :url (url/ingest-url provider-id concept-type native-id)
                 :headers headers
                 :accept accept-format
                 :throw-exceptions false
                 :connection-manager (s/conn-mgr)}
         params (merge params (when accept-format {:accept accept-format}))]
     (parse-ingest-response (client/request params) options))))

(defn multipart-param-request
  "Submits a multipart parameter request to the given url with the multipart parameters indicated.
  A multipart parameter is a map the keys :name, :content, and :content-type."
  [url multipart-params]

  ;; clj-http has a specific format for the multipart params.
  (let [multipart-params (for [{:keys [name content content-type]} multipart-params]
                           {:name name
                            :content content
                            :mime-type content-type
                            :encoding "UTF-8"})
        response (client/request {:method :post
                                  :url url
                                  :multipart multipart-params
                                  :accept :json
                                  :throw-exceptions false
                                  :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn validate-concept
  "Validate a concept and return a map with status and error messages if applicable."
  ([concept]
   (validate-concept concept {}))
  ([concept options]
   (let [{:keys [metadata format concept-type concept-id revision-id provider-id native-id]} concept
         {:keys [client-id validate-keywords]} options
         accept-format (get options :accept-format :xml)
         ;; added to allow testing of the raw response
         raw? (get options :raw? false)
         headers (util/remove-nil-keys {"Cmr-Concept-id" concept-id
                                        "Cmr-Revision-id" revision-id
                                        "Cmr-Validate-Keywords" validate-keywords
                                        "Client-Id" client-id})
         response (client/request
                    {:method :post
                     :url (url/validate-url provider-id concept-type native-id)
                     :body  metadata
                     :content-type format
                     :headers headers
                     :accept accept-format
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         status (:status response)]
     (if raw?
       response
       (let [parsed-response (parse-validate-response response options)]
         (if (not= status 200)
           ;; Validation only returns a response body if there are errors.
           parsed-response
           (select-keys parsed-response [:status :warnings])))))))

(defn validate-granule
  "Validates a granule concept by sending it and optionally its parent collection to the validation
  endpoint"
  ([granule-concept]
   ;; Use the regular validation endpoint in this case.
   (validate-concept granule-concept))
  ([granule-concept collection-concept]
   (let [{:keys [provider-id native-id]} granule-concept]
     (multipart-param-request
       (url/validate-url provider-id :granule native-id)
       [{:name "granule"
         :content (:metadata granule-concept)
         :content-type (:format granule-concept)}
        {:name "collection"
         :content (:metadata collection-concept)
         :content-type (:format collection-concept)}]))))

(defn ingest-concepts
  "Ingests all the given concepts assuming that they should all be successful."
  ([concepts]
   (ingest-concepts concepts nil))
  ([concepts options]
   (doseq [concept concepts]
     (is (= {:status 200
             :concept-id (:concept-id concept)
             :revision-id (:revision-id concept)}
            (ingest-concept concept options))))))

(defn delete-concepts
  "Deletes all the given concepts assuming that they should all be successful."
  ([concepts]
   (delete-concepts concepts nil))
  ([concepts options]
   (doseq [concept concepts]
     (is (#{404 200} (:status (delete-concept concept options)))))))

(defmulti parse-bulk-update-body
  "Parse the bulk update response body as a given format"
  (fn [response-format body]
    response-format))

(defmethod parse-bulk-update-body :xml
  [response-format response]
  (try
    (let [xml-elem (x/parse-str (:body response))]
      (if-let [errors (seq (cx/strings-at-path xml-elem [:error]))]
        (parse-xml-error-response-elem xml-elem)
        {:task-id (cx/string-at-path xml-elem [:task-id])
         :status (:status response)}))
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response)) e))))))

(defmethod parse-bulk-update-body :json
  [response-format response]
  (try
    (json/parse-string (:body response) true)
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response))) e)))))

(defn parse-bulk-update-response
 "Parse an bulk update response and append a status"
 [response options]
 (if (get options :raw? false)
   response
   (assoc (parse-bulk-update-body (or (:accept-format options) :xml) response)
          :status (:status response))))

(defn bulk-update-collections
  "Call ingest collection bulk update by provider"
  ([provider-id request-body]
   (bulk-update-collections provider-id request-body nil))
  ([provider-id request-body options]
   (let [accept-format (get options :accept-format :xml)
         token (:token options)
         user-id (:user-id options)
         headers (when (or user-id token)
                   (util/remove-nil-keys
                    {transmit-config/token-header token
                     "user-id" user-id}))
         params {:method :post
                 :url (url/ingest-collection-bulk-update-url provider-id)
                 :body (json/generate-string request-body)
                 :connection-manager (s/conn-mgr)
                 :throw-exceptions false}
         params (merge params (when accept-format {:accept accept-format}))
         params (merge params (when headers {:headers headers}))
         response (client/request params)]
     (parse-bulk-update-response response options))))

(defn bulk-update-granules
  "Call ingest granule bulk update by provider"
  [provider-id request-body options]
  (let [{:keys [token user-id]} options
        accept-format (get options :accept-format :xml)
        headers (when (or user-id token)
                  (util/remove-nil-keys
                   {transmit-config/token-header token
                    "user-id" user-id}))
        params {:method :post
                :url (url/ingest-granule-bulk-update-url provider-id)
                :body (json/generate-string request-body)
                :connection-manager (s/conn-mgr)
                :throw-exceptions false}
        response (-> params
                     (merge (when accept-format {:accept accept-format}))
                     (merge params (when headers {:headers headers}))
                     client/request)]
    (parse-bulk-update-response response options)))

(defmulti parse-bulk-update-provider-status-body
  "Parse the bulk update provider status response body as a given format"
  (fn [response-format body]
    response-format))

(defmethod parse-bulk-update-provider-status-body :default
  [response-format response]
  (try
    (let [xml-elem (x/parse-str (:body response))]
      (if-let [errors (seq (cx/strings-at-path xml-elem [:error]))]
        (parse-xml-error-response-elem xml-elem)
        {:tasks (seq (for [task (cx/elements-at-path xml-elem [:tasks :task])]
                      {:created-at (cx/string-at-path task [:created-at])
                       :name (cx/string-at-path task [:name])
                       :task-id (cx/string-at-path task [:task-id])
                       :status (cx/string-at-path task [:status])
                       :status-message (cx/string-at-path task [:status-message])
                       :request-json-body (cx/string-at-path task [:request-json-body])}))}))
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response)) e))))))

(defmethod parse-bulk-update-provider-status-body :json
  [response-format response]
  (try
    (json/parse-string (:body response) true)
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response))) e)))))

(defn parse-bulk-update-provider-status-response
 "Parse an bulk update provider status response and append a status"
 [response options]
 (if (get options :raw? false)
   response
   (assoc (parse-bulk-update-provider-status-body (or (:accept-format options) :xml) response)
          :status (:status response))))

(defn- bulk-update-provider-status*
  "Get the tasks and statuses by provider"
  [concept-type provider-id options]
  (let [accept-format (get options :accept-format :xml)
        token (:token options)
        task-url (if (= :collection concept-type)
                   (url/ingest-collection-bulk-update-status-url provider-id)
                   (url/ingest-granule-bulk-update-status-url provider-id))
        params {:method :get
                :url task-url
                :connection-manager (s/conn-mgr)
                :throw-exceptions false}
        params (merge params (when accept-format {:accept accept-format}))
        params (merge params (when token {:headers {transmit-config/token-header token}}))
        response (client/request params)]
    (parse-bulk-update-provider-status-response response options)))

(defn update-granule-bulk-update-task-statuses
  "Force an unscheduled update of granule bulk update task status."
  []
  (let [params {:method :post
                :url (url/ingest-granule-bulk-update-task-status-url)
                :connection-manager (s/conn-mgr)
                :throw-exceptions false
                :headers {transmit-config/token-header
                          (transmit-config/echo-system-token)}}]
    (client/request params)))

(defn bulk-update-provider-status
  "Get the tasks and statuses by provider"
  ([provider-id]
   (bulk-update-provider-status provider-id nil))
  ([provider-id options]
   (bulk-update-provider-status* :collection provider-id options)))

(defn granule-bulk-update-tasks
  "Get the granule bulk update tasks by provider"
  ([provider-id]
   (granule-bulk-update-tasks provider-id nil))
  ([provider-id options]
   (bulk-update-provider-status* :granule provider-id options)))

(defn update-granule-bulk-update-tasks
  "Updates the granule bulk update tasks by provider"
  [provider-id options]
  (bulk-update-provider-status* :granule provider-id options))

(defmulti parse-bulk-update-task-status-body
  "Parse the bulk update task status response body as a given format"
  (fn [response-format body]
    response-format))

(defmethod parse-bulk-update-task-status-body :xml
  [response-format response]
  (try
    (let [xml-elem (x/parse-str (:body response))]
      (if-let [errors (seq (cx/strings-at-path xml-elem [:error]))]
        (parse-xml-error-response-elem xml-elem)
        {:created-at (cx/string-at-path xml-elem [:created-at])
         :name (cx/string-at-path xml-elem [:name])
         :task-status (cx/string-at-path xml-elem [:task-status])
         :status-message (cx/string-at-path xml-elem [:status-message])
         :request-json-body (cx/string-at-path xml-elem [:request-json-body])
         :collection-statuses
          (seq (for [status (cx/elements-at-path xml-elem [:collection-statuses :collection-status])]
                {:concept-id (cx/string-at-path status [:concept-id])
                 :status (cx/string-at-path status [:status])
                 :status-message (cx/string-at-path status [:status-message])}))}))
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response)) e))))))

(defmethod parse-bulk-update-task-status-body :json
  [response-format response]
  (try
    (json/parse-string (:body response) true)
    (catch Exception e
      (throw (Exception. (str "Error parsing ingest body: " (pr-str (:body response))) e)))))

(defn parse-bulk-update-task-status-response
  "Parse bulk update task status response and append the response status"
  ([response options]
   (parse-bulk-update-task-status-response response options :xml))
  ([response options default-result-format]
   (if (get options :raw? false)
     response
     (assoc (parse-bulk-update-task-status-body
             (or (:accept-format options) default-result-format)
             response)
            :status (:status response)))))

(defn- bulk-update-task-status*
  "Implementation function to get the bulk update task status for the given task id"
  [concept-type provider-id task-id options]
  (let [accept-format (get options :accept-format)
        query-params (get options :query-params)
        token (:token options)
        task-status-url (if (= :collection concept-type)
                          (url/ingest-collection-bulk-update-task-status-url provider-id task-id)
                          (url/ingest-granule-bulk-update-task-status-url task-id))
        params {:method :get
                :url task-status-url
                :connection-manager (s/conn-mgr)
                :throw-exceptions false}
        params (merge params (when query-params {:query-params query-params}))
        params (merge params (when accept-format {:accept accept-format}))
        params (merge params (when token {:headers {transmit-config/token-header token}}))
        default-result-format (if (= :collection concept-type) :xml :json)
        response (client/request params)]
    (parse-bulk-update-task-status-response response options default-result-format)))

(defn bulk-update-task-status
 "Get collection bulk update task status for the given task id"
 ([provider-id task-id]
  (bulk-update-task-status provider-id task-id nil))
 ([provider-id task-id options]
  (bulk-update-task-status* :collection provider-id task-id options)))

(defn granule-bulk-update-task-status
 "Get granule bulk update task status for the given task id"
 ([task-id]
  (granule-bulk-update-task-status task-id nil))
 ([task-id options]
  (bulk-update-task-status* :granule nil task-id options)))

(defn get-ingest-update-acls
  "Get a token's system ingest management update ACLs."
  [token]
  (-> (s/context)
      (assoc :token token)
      (acl/get-permitting-acls :system-object
                               echo-util/ingest-management-acl
                               :update)))

;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-ingest-umm-version-to-current
  "Set the ingest-accept-umm-version to the latest UMM version defined in umm-spec-lib."
  []
  (side/eval-form `(common-config/set-collection-umm-version!
                     umm-versioning/current-collection-version))
  (side/eval-form `(ingest-config/set-variable-umm-version!
                     umm-versioning/current-variable-version))
  (side/eval-form `(ingest-config/set-service-umm-version!
                     umm-versioning/current-service-version))
  (side/eval-form `(ingest-config/set-tool-umm-version!
                     umm-versioning/current-tool-version))
  (side/eval-form `(ingest-config/set-subscription-umm-version!
                     umm-versioning/current-subscription-version)))

(defn create-provider
  "Creates a provider along with ACLs to create and access the providers data.
  Provider map should contain fields with details for an individual provider like what would be
  taken on the ingest api.

  Options:
  * grant-all-search? - Indicates whether all search acls should be granted for a provider. Default true
  * grant-all-ingest? - Indicates whether all ingest acls should be granted for a provider. Default true"
  ([provider-map]
   (create-provider provider-map {}))
  ([provider-map options]
   (let [{:keys [provider-guid provider-id short-name small cmr-only consortiums]} provider-map
         short-name (or short-name (:short-name options) provider-id)
         cmr-only (if (some? cmr-only) cmr-only (get options :cmr-only true))
         small (if (some? small) small (get options :small false))
         grant-all-search? (get options :grant-all-search? true)
         grant-all-ingest? (get options :grant-all-ingest? true)
         grant-all-access-control? (get options :grant-all-access-control? true)]
     (create-mdb-provider {:provider-id provider-id
                           :short-name short-name
                           :cmr-only cmr-only
                           :small small
                           :consortiums consortiums})
     ;; Create provider in mock echo with the guid set to the ID to make things easier to sync up
     (echo-util/create-providers (s/context) {provider-id provider-id})

     (when grant-all-search?
       (echo-util/grant (s/context)
                        [echo-util/guest-read-ace
                         echo-util/registered-user-read-ace]
                        :catalog_item_identity
                        (assoc (echo-util/catalog-item-id provider-id)
                               :collection_applicable true
                               :granule_applicable true)))
     (when grant-all-ingest?
       (echo-util/grant-all-ingest (s/context) provider-id))

     (when grant-all-access-control?
       (echo-util/grant-system-group-permissions-to-all (s/context))
       (echo-util/grant-provider-group-permissions-to-all (s/context) provider-id)))))

(def reset-fixture-default-options
  {:grant-all-search? true
   :grant-all-ingest? true
   :grant-all-access-control? true})

(defn setup-providers
  "Creates the given providers in CMR. Providers can be passed in
  two ways:
  1) a map of provider-guids to provider-ids
     {'provider-guid1' 'PROV1' 'provider-guid2' 'PROV2'}, or
  2) a list of provider attributes maps:
     [{:provider-guid 'provider-guid1' :provider-id 'PROV1'
       :short-name 'provider short name'}...]"
  ([providers]
   (setup-providers providers nil))
  ([providers options]
   (let [{:keys [grant-all-search? grant-all-ingest? grant-all-access-control?]}
         (merge reset-fixture-default-options options)
         providers (if (sequential? providers)
                       providers
                       (for [[provider-guid provider-id consortiums] providers]
                         {:provider-guid provider-guid
                          :provider-id provider-id
                          :consortiums consortiums}))]
      (doseq [provider-map providers]
        (create-provider
         provider-map
         {:grant-all-search? grant-all-search?
          :grant-all-ingest? grant-all-ingest?
          :grant-all-access-control? grant-all-access-control?})))))

(defn setup-providers-with-customized-options
  "Creates the given providers in CMR. Providers can be passed in
  two ways:
  1) a map of provider-guids to provider-ids
     {'provider-guid1' 'PROV1' 'provider-guid2' 'PROV2'}, or
  2) a list of provider attributes maps:
     [{:provider-guid 'provider-guid1' :provider-id 'PROV1'
       :short-name 'provider short name'}...]
  options are customized to individual providers:
  {'PROV1' {:grant-all-search? false :grant-all-ingest? false}
   'PROV2' {:grant-all-search? false :grant-all-ingest? true}}"
  ([providers options]
   (let [providers (if (sequential? providers)
                       providers
                       (for [[provider-guid provider-id consortiums] providers]
                         {:provider-guid provider-guid
                          :provider-id provider-id
                          :consortiums consortiums}))]
      (doseq [provider-map providers]
        (let [provider-options (merge reset-fixture-default-options (get options (:provider-id provider-map)))]
        (create-provider
         provider-map
         {:grant-all-search? (:grant-all-search? provider-options)
          :grant-all-ingest? (:grant-all-ingest? provider-options)
          :grant-all-access-control? (:grant-all-access-control? provider-options)}))))))

(defn reset-fixture
  "Resets all the CMR systems then uses the `set-ingest-umm-version-to-current`
  function to set the accepted umm versions for ingest to the the latest UMM version defined in
  umm-spec-lib, so that all the ingest tests are testing against the latest umm version.
  and uses the `setup-providers` function to create a testing fixture.

  For the format of the providers data structure, see `setup-providers`."
  ([]
   (reset-fixture {}))
  ([providers]
   (reset-fixture providers nil))
  ([providers options]
   (fn [f]
     (dev-sys-util/reset)
     (set-ingest-umm-version-to-current)
     (when (seq providers)
       (setup-providers providers options))
     (f))))

(defn reset-fixture-with-customized-options
  "Resets all the CMR systems then uses the `set-ingest-umm-version-to-current`
  function to set the accepted umm versions for ingest to the the latest UMM version defined in
  umm-spec-lib, so that all the ingest tests are testing against the latest umm version.
  and uses the `setup-providers` function to create a testing fixture.

  For the format of the providers data structure, see `setup-providers`."
  ([]
   (reset-fixture {}))
  ([providers]
   (reset-fixture providers nil))
  ([providers options]
   (fn [f]
     (dev-sys-util/reset)
     (set-ingest-umm-version-to-current)
     (when-not (empty? providers)
      (setup-providers-with-customized-options providers options))
     (f))))

(defn grant-all-search
  "Grants all users access to search for any collections or granules from the passed in list of
  provider ids."
  [provider-ids]
  (doseq [provider-id provider-ids]
    (echo-util/grant (s/context)
                     [echo-util/guest-read-ace
                      echo-util/registered-user-read-ace]
                     :catalog_item_identity
                     (assoc (echo-util/catalog-item-id provider-id)
                            :collection_applicable true
                            :granule_applicable true))))

(defn grant-all-search-fixture
  "Fixture to grant all users search access for the provider-ids passed in."
  [provider-ids]
  (fn [f]
    (grant-all-search provider-ids)
    (f)))

(defn clear-caches
  "Clears caches in the ingest application"
  []
  (client/post (url/dev-system-clear-cache-url)
               {:connection-manager (s/conn-mgr)
                :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
  (client/post (url/ingest-clear-cache-url)
               {:connection-manager (s/conn-mgr)
                :headers {transmit-config/token-header (transmit-config/echo-system-token)}}))
