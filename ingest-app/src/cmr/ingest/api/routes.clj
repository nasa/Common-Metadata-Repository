(ns cmr.ingest.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cheshire.core :as cheshire]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.jobs :as common-jobs]
            [cmr.acl.core :as acl]
            [cmr.ingest.services.ingest :as ingest]
            [cmr.system-trace.http :as http-trace]
            [cmr.ingest.services.jobs :as jobs]
            [cmr.ingest.api.provider :as provider-api]))

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
  (first (string/split concept-type #";")))

(defn- body->concept
  "Create a metadata concept from the given request body"
  [concept-type provider-id native-id body content-type headers]
  (let [metadata (string/trim (slurp body))
        base-concept {:metadata metadata
                      :format (sanitize-concept-type content-type)
                      :provider-id provider-id
                      :native-id native-id
                      :concept-type concept-type}]
    (set-concept-id base-concept headers)))

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
            (r/response (ingest/validate-concept request-context
                                                 (body->concept
                                                   :collection
                                                   provider-id
                                                   native-id
                                                   body
                                                   content-type
                                                   headers)))))

        (context ["/collections/:native-id" :native-id #".*$"] [native-id]
          (PUT "/" {:keys [body content-type headers request-context params]}
            (let [context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :update "PROVIDER_OBJECT" provider-id)
              (r/response (ingest/save-concept request-context
                                               (body->concept
                                                 :collection
                                                 provider-id
                                                 native-id
                                                 body
                                                 content-type
                                                 headers)))))
          (DELETE "/" {:keys [request-context params headers]}
            (let [concept-attribs {:provider-id provider-id
                                   :native-id native-id
                                   :concept-type :collection}
                  context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :delete "PROVIDER_OBJECT" provider-id)
              (r/response (ingest/delete-concept request-context concept-attribs)))))

        (context ["/validate/granule/:native-id" :native-id #".*$"] [native-id]
          (POST "/" {:keys [body content-type headers request-context]}
            (r/response (ingest/validate-concept request-context
                                                 (body->concept
                                                   :granule
                                                   provider-id
                                                   native-id
                                                   body
                                                   content-type
                                                   headers)))))

        (context ["/granules/:native-id" :native-id #".*$"] [native-id]
          (PUT "/" {:keys [body content-type headers request-context params]}
            (let [context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :update "PROVIDER_OBJECT" provider-id)
              (r/response (ingest/save-concept request-context
                                               (body->concept
                                                 :granule
                                                 provider-id
                                                 native-id
                                                 body
                                                 content-type
                                                 headers)))))
          (DELETE "/" {:keys [request-context params headers]}
            (let [concept-attribs {:provider-id provider-id
                                   :native-id native-id
                                   :concept-type :granule}
                  context (acl/add-authentication-to-context request-context params headers)]
              (acl/verify-ingest-management-permission
                context :delete "PROVIDER_OBJECT" provider-id)
              (r/response (ingest/delete-concept request-context concept-attribs))))))

      (context "/jobs" []
        ;; pause all jobs
        (POST "/pause" {:keys [request-context params headers]}
          (let [context (acl/add-authentication-to-context request-context params headers)]
            (acl/verify-ingest-management-permission context :update)
            (common-jobs/pause-jobs)))

        ;; resume all jobs
        (POST "/resume" {:keys [request-context params headers]}
          (let [context (acl/add-authentication-to-context request-context params headers)]
            (acl/verify-ingest-management-permission context :update)
            (common-jobs/resume-jobs))))

      (GET "/health" {request-context :request-context params :params}
        (let [{pretty? :pretty} params
              {:keys [ok? dependencies]} (ingest/health request-context)]
          {:status (if ok? 200 503)
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (cheshire/generate-string dependencies {:pretty pretty?})})))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      (errors/exception-handler (fn [_] "application/json"))
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))


