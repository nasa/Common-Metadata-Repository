(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as str]
            [clojure.set :as set]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [cmr.ingest.api.multipart :as mp]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cheshire.core :as cheshire]
            [clojure.data.xml :as x]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as svc-errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api :as api]
            [cmr.common.api.errors :as api-errors]
            [cmr.common.services.errors :as srvc-errors]
            [cmr.common.jobs :as common-jobs]
            [cmr.common.mime-types :as mt]
            [cmr.acl.core :as acl]
            [cmr.ingest.services.ingest-service :as ingest]
            [cmr.system-trace.http :as http-trace]
            [cmr.ingest.services.jobs :as jobs]
            [cmr.ingest.api.provider :as provider-api]
            [cmr.ingest.services.messages :as msg]
            [cmr.common-app.api.routes :as common-routes]
            [cmr.common-app.api-docs :as api-docs]
            [cmr.ingest.services.providers-cache :as pc]))

(def ECHO_CLIENT_ID "ECHO")

(defn- verify-provider-cmr-only-against-client-id
  "Verifies provider CMR-ONLY flag matches the client-id in the request.
  Throws bad request error if the client-id is Echo when the provider is CMR-ONLY
  or the client id is not Echo when the provider is not CMR-ONLY."
  [provider-id cmr-only client-id]
  (when (nil? cmr-only)
    (srvc-errors/internal-error!
      (format "CMR Only should not be nil, but is for Provider %s." provider-id)))
  (when (or (and cmr-only (= ECHO_CLIENT_ID client-id))
            (and (not cmr-only) (not= ECHO_CLIENT_ID client-id)))
    (let [msg (if cmr-only
                (format
                  (str "Provider %s was configured as CMR Only which only allows ingest directly "
                       "through the CMR. It appears from the client id that it was sent from ECHO.")
                  provider-id)
                (format
                  (str "Provider %s was configured as false for CMR Only which only allows "
                       "ingest indirectly through ECHO. It appears from the client id [%s] "
                       "that ingest was not sent from ECHO.")
                  provider-id client-id))]
      (srvc-errors/throw-service-error :bad-request msg))))

(defn verify-provider-against-client-id
  "Verifies the given provider's CMR-ONLY flag matches the client-id in the request."
  [context provider-id]
  (let [cmr-only (->> (pc/get-providers-from-cache context)
                      (some #(when (= provider-id (:provider-id %)) %))
                      :cmr-only)
        client-id (:client-id context)]
    (verify-provider-cmr-only-against-client-id provider-id cmr-only client-id)))

(def valid-response-mime-types
  "Supported ingest response formats"
  #{"*/*" "application/xml" "application/json"})

(def content-type-mime-type->response-format
  "A map of mime-types to supported response format"
  {"application/echo10+xml" :xml
   "application/iso19115+xml" :xml
   "application/iso:smap+xml" :xml
   "application/dif+xml" :xml
   "application/dif10+xml" :xml
   "application/xml" :xml
   "application/json" :json})

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
  [m pretty?]
  (let [emit-fn (if pretty? x/indent-str x/emit-str)]
    (emit-fn
      (x/element :result {}
                 (reduce-kv (fn [memo k v]
                              (conj memo (x/element (keyword k) {} v)))
                            []
                            m)))))

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

(defmulti generate-ingest-response
  "Convert a result to a proper response format"
  (fn [headers result]
    (get-ingest-result-format headers :xml)))

(defmethod generate-ingest-response :json
  [headers result]
  ;; ring-json middleware will handle converting the body to json
  {:status 200 :body result})

(defmethod generate-ingest-response :xml
  [headers result]
  (let [pretty? (api/pretty-request? nil headers)]
    {:status 200 :body (result-map->xml result pretty?)}))

(defn- set-concept-id-and-revision-id
  "Set concept-id and revision-id for the given concept based on the headers. Ignore the
  revision-id if no concept-id header is passed in."
  [concept headers]
  (let [concept-id (get headers "concept-id")
        revision-id (get headers "revision-id")]
    (if concept-id
      (if revision-id
        (try
          (assoc concept :concept-id concept-id :revision-id (Integer/parseInt revision-id))
          (catch NumberFormatException e
            (srvc-errors/throw-service-error
              :bad-request
              (msg/invalid-revision-id revision-id))))
        (assoc concept :concept-id concept-id))
      concept)))

(defn- sanitize-concept-type
  "Drops the parameter part of the MediaTypes from concept-type and returns the type/sub-type part"
  [concept-type]
  (first (str/split concept-type #";")))

(defn- body->concept
  "Create a metadata concept from the given request body"
  [concept-type provider-id native-id body content-type headers]
  (let [metadata (str/trim (slurp body))]
    (-> {:metadata metadata
         :format (sanitize-concept-type content-type)
         :provider-id provider-id
         :native-id native-id
         :concept-type concept-type}
        (set-concept-id-and-revision-id headers))))

(defn- concept->loggable-string
  "Returns a string with information about the concept as a loggable string."
  [concept]
  (pr-str (dissoc concept :metadata)))

(defmulti validate-granule
  "Validates the granule in the request. It can handle a granule and collection sent as multipart-params
  or a normal request with the XML as the body."
  (fn [context provider-id native-id request]
    (if (seq (:multipart-params request))
      :multipart-params
      :default)))

(defmethod validate-granule :default
  [context provider-id native-id {:keys [body content-type headers request-context] :as request}]
  (let [concept (body->concept :granule provider-id native-id body content-type headers)]
    (info (format "Validating granule %s from client %s"
                  (concept->loggable-string concept) (:client-id context)))
    (ingest/validate-granule request-context concept)))

(defn- multipart-param->concept
  "Converts a multipart parameter "
  [provider-id native-id concept-type {:keys [content-type content]}]
  {:metadata content
   :format (sanitize-concept-type content-type)
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
  [context provider-id native-id {:keys [multipart-params request-context]}]
  (validate-multipart-params #{"granule" "collection"} multipart-params)

  (let [coll-concept (multipart-param->concept
                       provider-id native-id :collection (get multipart-params "collection"))
        gran-concept (multipart-param->concept
                       provider-id native-id :granule (get multipart-params "granule"))]
    (ingest/validate-granule-with-parent-collection request-context gran-concept coll-concept)))

(defn ingest-routes
  "Create the routes for ingest, validate, and delete operations"
  []
  (api-errors/set-default-error-format
    :xml
    (context "/providers/:provider-id" [provider-id]

      (context ["/validate/collection/:native-id" :native-id #".*$"] [native-id]

        (POST "/" {:keys [body content-type params headers request-context]}
          (let [concept (body->concept :collection provider-id native-id body content-type headers)]
            (verify-provider-against-client-id request-context provider-id)
            (info (format "Validating Collection %s from client %s"
                          (concept->loggable-string concept) (:client-id request-context)))
            (ingest/validate-concept request-context concept)
            {:status 200})))
      (context ["/collections/:native-id" :native-id #".*$"] [native-id]
        (PUT "/" {:keys [body content-type headers request-context params]}
          (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
          (verify-provider-against-client-id request-context provider-id)
          (let [concept (body->concept :collection provider-id native-id body content-type headers)]
            (info (format "Ingesting collection %s from client %s"
                          (concept->loggable-string concept) (:client-id request-context)))
            (generate-ingest-response headers (ingest/save-concept request-context concept))))
        (DELETE "/" {:keys [request-context params headers]}
          (let [concept-attribs {:provider-id provider-id
                                 :native-id native-id
                                 :concept-type :collection}]
            (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
            (verify-provider-against-client-id request-context provider-id)
            (info (format "Deleting collection %s from client %s"
                          (pr-str concept-attribs) (:client-id request-context)))
            (generate-ingest-response headers (ingest/delete-concept request-context concept-attribs)))))

      (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
        (POST "/" {:keys [params headers request-context] :as request}
          (verify-provider-against-client-id request-context provider-id)
          (validate-granule request-context provider-id native-id request)
          {:status 200}))

      (context ["/granules/:native-id" :native-id #".*$"] [native-id]
        (PUT "/" {:keys [body content-type headers request-context params]}
          (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
          (verify-provider-against-client-id request-context provider-id)
          (let [concept (body->concept :granule provider-id native-id body content-type headers)]
            (info (format "Ingesting granule %s from client %s"
                          (concept->loggable-string concept) (:client-id request-context)))
            (generate-ingest-response headers (ingest/save-concept request-context concept))))
        (DELETE "/" {:keys [request-context params headers]}
          (let [concept-attribs {:provider-id provider-id
                                 :native-id native-id
                                 :concept-type :granule}]
            (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
            (verify-provider-against-client-id request-context provider-id)
            (info (format "Deleting granule %s from client %s"
                          (pr-str concept-attribs) (:client-id request-context)))
            (generate-ingest-response headers (ingest/delete-concept request-context concept-attribs))))))))


(defn- build-routes [system]
  (routes
    (context (get-in system [:ingest-public-conf :relative-root-url]) []
      provider-api/provider-api-routes

      ;; Add routes to create, update, delete, validate concepts
      (ingest-routes)

      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:ingest-public-conf :protocol])
                            (get-in system [:ingest-public-conf :relative-root-url])
                            "public/ingest_index.html")

      ;; add routes for managing jobs
      (common-routes/job-api-routes
        (routes
          (POST "/reindex-collection-permitted-groups" {:keys [headers params request-context]}
            (acl/verify-ingest-management-permission request-context :update)
            (jobs/reindex-collection-permitted-groups request-context)
            {:status 200})
          (POST "/reindex-all-collections" {:keys [headers params request-context]}
            (acl/verify-ingest-management-permission request-context :update)
            (jobs/reindex-all-collections request-context)
            {:status 200})
          (POST "/cleanup-expired-collections" {:keys [headers params request-context]}
            (acl/verify-ingest-management-permission request-context :update)
            (jobs/cleanup-expired-collections request-context)
            {:status 200})))

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for checking health of the application
      (common-routes/health-api-routes ingest/health))

    (route/not-found "Not Found")))

(defn default-error-format-fn
  "Determine the format that errors should be returned in based on the default-format
  key set on the ExceptionInfo object passed in as parameter e. Defaults to json if
  the default format has not been set to :xml."
  [_request e]
  (mt/format->mime-type (:default-format (ex-data e))))

(defn make-api [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      (http-trace/build-request-context-handler system)
      handler/site
      api-errors/invalid-url-encoding-handler
      mp/wrap-multipart-params
      ring-json/wrap-json-body
      ring-json/wrap-json-response
      (api-errors/exception-handler default-error-format-fn)))

