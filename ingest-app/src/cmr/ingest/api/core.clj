(ns cmr.ingest.api.core
  "Supports the ingest API definitions."
  (:require
   [cheshire.core :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as srvc-errors]
   [cmr.common.xml.gen :refer :all]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.messages :as msg]
   [cmr.ingest.services.providers-cache :as pc]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.common.util :as util]
   [ring.util.codec :as ring-codec])
  (:import
   (clojure.lang ExceptionInfo)))

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

(defn- create-nested-elements
  "Create nested elements for the nested maps."
  [m]
  (reduce-kv (fn [memo k v]
                 (conj memo (xml/element (keyword k) {} (if (map? v)
                                                          (create-nested-elements v)
                                                          v))))
             []
             m))

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
  (xml/emit-str
   (xml/element
    :result
    {}
    (create-nested-elements m))))

(defn get-ingest-result-format
  "Returns the requested ingest result format parsed from the Accept header or :xml
  if no Accept header is given"
  ([headers default-format]
   (get-ingest-result-format
     headers (set (keys content-type-mime-type->response-format)) default-format))
  ([headers valid-mime-types default-format]
   (get content-type-mime-type->response-format
        (mt/extract-header-mime-type valid-response-mime-types headers "accept" true)
        default-format)))

(defn ingest-status-code
  "Returns the ingest status code when ingest is successful.

  If the ingest is newly created (a revision id of 1), return HTTP status
  'Created'."
  [result]
  (if (= 1 (:revision-id result))
    201
    200))

(defn format-and-contextualize-warnings-existing-errors
  "Format and add a message to warnings and existing-errors to make translation issues more clear to the user."
  [result]
  (let [warning-context "After translating item to UMM-C the metadata had the following issue(s): "
        err-context "After translating item to UMM-C the metadata had the following existing error(s): "]
    (-> result
        (update :warnings
                (fn [warnings]
                  (when (not-empty warnings)
                    [(str warning-context (string/join ";; " warnings))])))
        (update :existing-errors
                (fn [existing-errors]
                  (when (not-empty existing-errors)
                    [(str err-context (string/join ";; " existing-errors))]))))))

(defmulti generate-ingest-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (get-ingest-result-format headers :xml)))

(defmethod generate-ingest-response :json
  [headers result]
  ;; ring-json middleware will handle converting the body to json
  {:status (or (:status result)
               (ingest-status-code result))
   :headers {"Content-Type" (mt/format->mime-type :json)}
   :body result})

(defmethod generate-ingest-response :xml
  [headers result]
  {:status (or (:status result)
               (ingest-status-code result))
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

(defn set-revision-id
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

(defn get-user-id-from-token
  "Get user id based on token in request context"
  [context]
  (when-let [token (:token context)]
    (cache/get-value (cache/context->cache context user-id-cache-key)
                     token
                     (partial tokens/get-user-id context token))))

(defn get-user-id
  "Get user id based on context and headers"
  [context headers]
  (if-let [user-id (get headers transmit-config/user-id-header)]
    user-id
    (get-user-id-from-token context)))

(defn set-user-id
  "Associate user id to concept."
  [concept context headers]
  (assoc concept :user-id (get-user-id context headers)))

(defn- set-concept-id
  "Set concept-id and revision-id for the given concept based on the headers. Ignore the
  revision-id if no concept-id header is passed in."
  [concept headers]
  ;; The header concept-id exists primarily to support backwards compatibility with Catalog Rest
  (if-let [concept-id (or (get headers "cmr-concept-id") (get headers "concept-id"))]
    (assoc concept :concept-id concept-id)
    concept))

(defn read-body!
   "Returns the body content string by slurping the request body.
    This function has the side effect of emptying the request body.
    Don't try to read the body again after calling this function."
  [body]
  (string/trim (slurp body)))

(defn read-multiple-body!
  "Returns the body content string by slurping the request body. This function
   has the side effect of emptying the request body. Don't try to read the body
   again after calling this function.

   For some calls, specifically variables, two documents are sent in. These
   documents are Metadata Content and Extended Data. Metadata Content is a
   metadata JSON record, like UMM-V, where Extended Data is any valid JSON which
   is intended to be used as data for a Relationship.

   Note: this function assumes JSON and not XML as the input."
  [body]
  (let [body-str (read-body! body)]
    (try
      (let [body-map (json/parse-string body-str)
            content (get body-map "content")
            data (get body-map "data")]
        (if (and (some? content) (some? data))
          [(json/generate-string content) (json/generate-string data)]
          [body-str]))
      (catch com.fasterxml.jackson.core.JsonParseException jpe
        ;; other blocks of code will check the validity of the body, but at this
        ;; point we may be looking at XML and not JSON, just return the content
        ;; of body and stop looking for Association Data
        [body-str]))))

(defn- metadata->concept
  "Create a metadata concept from the given metadata"
  [concept-type metadata content-type headers]
  (-> {:metadata (first metadata)
       :data (second metadata) ;; may be nil if not provided
       :format (mt/keep-version content-type)
       :concept-type concept-type}
      (set-concept-id headers)
      (set-revision-id headers)
      (util/remove-nil-keys)))

(defn body->concept!
  "Create a metadata concept from the given request body.
  This function has the side effect of emptying the request body.
  Don't try to read the body again after calling this function."
  ([concept-type body content-type headers]
   (let [metadata (read-multiple-body! body)]
     (metadata->concept concept-type metadata content-type headers)))
  ([concept-type native-id body content-type headers]
   (assoc (body->concept! concept-type body content-type headers)
          :native-id native-id))
  ([concept-type provider-id native-id body content-type headers]
   (assoc (body->concept! concept-type native-id body content-type headers)
          :provider-id provider-id)))

(defn concept-with-revision-id
  "Returns a concept with concept-id and revision-id added for logging purpose."
  [concept result]
  (merge concept (select-keys result [:concept-id :revision-id])))

(defn concept->loggable-string
  "Returns a string with information about the concept as a loggable string."
  [concept]
  (pr-str (dissoc concept :metadata)))

(defn log-concept-with-metadata-size
  "Log the concept with metadata-size-bytes added, and metadata removed."
  [concept request-context]
  (let [metadata-size-bytes (-> (:metadata concept) (.getBytes "UTF-8") alength)
        concept (assoc concept :metadata-size-bytes metadata-size-bytes)]
    (info (format "Successfully ingested %s from client %s, token_type %s"
                  (concept->loggable-string concept)
                  (:client-id request-context)
                  (lt-validation/get-token-type (:token request-context))))))

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

(defn delete-concept
  "Delete the given concept by its concept type, provider id and native id."
  [concept-type provider-id native-id request]
  (let [{:keys [request-context params headers]} request
        concept-attribs (-> {:provider-id provider-id
                             :native-id native-id
                             :concept-type concept-type}
                            (set-revision-id headers)
                            (set-user-id request-context headers))]
    (lt-validation/validate-launchpad-token request-context)
    (common-enabled/validate-write-enabled request-context "ingest")
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (info (format "Deleting %s %s from client %s"
                  (name concept-type) (pr-str concept-attribs) (:client-id request-context)))
    (generate-ingest-response headers
                              (format-and-contextualize-warnings-existing-errors
                               (ingest/delete-concept
                                request-context
                                concept-attribs)))))
