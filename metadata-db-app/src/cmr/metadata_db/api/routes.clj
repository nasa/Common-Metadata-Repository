(ns cmr.metadata-db.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [cmr.common.jobs :as jobs]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.cache :as cache]
            [cmr.system-trace.http :as http-trace]
            [cmr.metadata-db.services.health-service :as hs]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.jobs :as mdb-jobs]
            [cmr.acl.core :as acl]
            [cmr.metadata-db.api.provider :as provider-api]
            [cmr.metadata-db.api.concepts :as concepts-api]))

(def cache-api-routes
  "The routes for the cache querying/management api"
  (context "/caches" []
    ;; Get the list of caches
    (GET "/" {:keys [params request-context headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [caches (map name (keys (get-in context [:system :caches])))]
          (acl/verify-ingest-management-permission context :read)
          {:status 200
           :body (json/generate-string caches)})))
    ;; Get the keys for the given cache
    (GET "/:cache-name" {{:keys [cache-name] :as params} :params
                         request-context :request-context
                         headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [cache (cache/context->cache context (keyword cache-name))]
          (when cache
            (let [result (cache/cache-keys cache)]
              {:status 200
               :body (json/generate-string result)})))))

    ;; Get the value for the given key for the given cache
    (GET "/:cache-name/:cache-key" {{:keys [cache-name cache-key] :as params} :params
                                    request-context :request-context
                                    headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [cache-key (keyword cache-key)
              cache (cache/context->cache context (keyword cache-name))
              result (cache/cache-lookup cache cache-key)]
          (when result
            {:status 200
             :body (json/generate-string result)}))))

    (POST "/clear-cache" {:keys [request-context params headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (cache/reset-caches context))
      {:status 200})))

(def admin-api-routes
  "The administrative control routes for metadata db."
  (routes
    ;; delete the entire database
    (POST "/reset" {:keys [request-context params headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (cache/reset-caches request-context)
        (concept-service/reset context)
        {:status 204}))

    (context "/jobs" []
      ;; pause all jobs
      (POST "/pause" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (jobs/pause-jobs)))

      ;; resume all jobs
      (POST "/resume" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (jobs/resume-jobs)))

      ;; Trigger the old revision concept cleanup
      (POST "/old-revision-concept-cleanup" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (mdb-jobs/old-revision-concept-cleanup context)
          {:status 204}))

      (POST "/expired-concept-cleanup" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (mdb-jobs/expired-concept-cleanup context)
          {:status 204})))

    (GET "/health" {request-context :request-context params :params}
      (let [{pretty? :pretty} params
            {:keys [ok? dependencies]} (hs/health request-context)]
        {:status (if ok? 200 503)
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body (json/generate-string dependencies {:pretty pretty?})}))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      concepts-api/concepts-api-routes
      provider-api/provider-api-routes
      cache-api-routes
      admin-api-routes)

    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      (errors/exception-handler (fn [_] "application/json"))
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





