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
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api :as api]
            [cmr.common.api.errors :as api-errors]
            [cmr.common.services.errors :as srvc-errors]
            [cmr.common.jobs :as common-jobs]
            [cmr.acl.core :as acl]
            [cmr.ingest.services.ingest :as ingest]
            [cmr.system-trace.http :as http-trace]
            [cmr.ingest.services.jobs :as jobs]
            [cmr.ingest.api.provider :as provider-api]
            [cmr.ingest.services.messages :as msg]
            [cmr.common-app.api.routes :as common-routes]))

(defn- set-concept-id
  "Set concept-id in concept if it is passed in the header"
  [concept headers]
  (let [concept-id (get headers "concept-id")]
    (if (empty? concept-id)
      concept
      (assoc concept :concept-id concept-id))))

(defn- sanitize-concept-type
  "Drops the parameter part of the MediaTypes from concept-type and returns the type/sub-type part"
  [concept-type]
  (first (str/split concept-type #";")))

(defn- body->concept
  "Create a metadata concept from the given request body"
  [concept-type provider-id native-id body content-type headers]
  (let [metadata (str/trim (slurp body))
        base-concept {:metadata metadata
                      :format (sanitize-concept-type content-type)
                      :provider-id provider-id
                      :native-id native-id
                      :concept-type concept-type}]
    (set-concept-id base-concept headers)))

(defmulti validate-granule
  "Validates the granule in the request. It can handle a granule and collection sent as multipart-params
  or a normal request with the XML as the body."
  (fn [provider-id native-id request]
    (if (seq (:multipart-params request))
      :multipart-params
      :default)))

(defmethod validate-granule :default
  [provider-id native-id {:keys [body content-type headers request-context] :as request}]
  (ingest/validate-granule
    request-context
    (body->concept :granule provider-id native-id body content-type headers)))

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
  [provider-id native-id {:keys [multipart-params request-context]}]
  (validate-multipart-params #{"granule" "collection"} multipart-params)

  (let [coll-concept (multipart-param->concept
                       provider-id native-id :collection (get multipart-params "collection"))
        gran-concept (multipart-param->concept
                       provider-id native-id :granule (get multipart-params "granule"))]
    (ingest/validate-granule-with-parent-collection request-context gran-concept coll-concept)))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      provider-api/provider-api-routes
      (POST "/reindex-collection-permitted-groups" {:keys [headers request-context]}
        (jobs/reindex-collection-permitted-groups request-context)
        {:status 200})
      (POST "/cleanup-expired-collections" {:keys [headers request-context]}
        (jobs/cleanup-expired-collections request-context)
        {:status 200})
      (context "/providers/:provider-id" [provider-id]
        (context ["/validate/collection/:native-id" :native-id #".*$"] [native-id]
          (POST "/" {:keys [body content-type headers request-context]}
            (ingest/validate-concept
              request-context
              (body->concept :collection provider-id native-id body content-type headers))
            {:status 200}))

        (context ["/collections/:native-id" :native-id #".*$"] [native-id]
          (PUT "/" {:keys [body content-type headers request-context params]}
            (let [context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :update "PROVIDER_OBJECT" provider-id)
              (r/response
                (ingest/save-concept
                  request-context
                  (body->concept :collection provider-id native-id body content-type headers)))))
          (DELETE "/" {:keys [request-context params headers]}
            (let [concept-attribs {:provider-id provider-id
                                   :native-id native-id
                                   :concept-type :collection}
                  context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :update "PROVIDER_OBJECT" provider-id)

              (r/response (ingest/delete-concept request-context concept-attribs)))))

        (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
          (POST "/" request
            (validate-granule provider-id native-id request)
            {:status 200}))

        (context ["/granules/:native-id" :native-id #".*$"] [native-id]
          (PUT "/" {:keys [body content-type headers request-context params]}
            (let [context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :update "PROVIDER_OBJECT" provider-id)
              (r/response
                (ingest/save-concept
                  request-context
                  (body->concept :granule provider-id native-id body content-type headers)))))
          (DELETE "/" {:keys [request-context params headers]}
            (let [concept-attribs {:provider-id provider-id
                                   :native-id native-id
                                   :concept-type :granule}
                  context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :update "PROVIDER_OBJECT" provider-id)
              (r/response (ingest/delete-concept request-context concept-attribs))))))

      ;; add routes for managing jobs
      common-routes/job-api-routes

      ;; add routes for accessing caches
      common-routes/cache-api-routes

      ;; add routes for checking health of the application
      (common-routes/health-api-routes ingest/health))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      handler/site
      api-errors/invalid-url-encoding-handler
      mp/wrap-multipart-params
      ring-json/wrap-json-body
      ring-json/wrap-json-response
      api-errors/exception-handler))

