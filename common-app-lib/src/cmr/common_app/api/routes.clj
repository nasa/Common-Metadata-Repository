(ns cmr.common-app.api.routes
  "Defines routes that are common across multiple applications."
  (:require [cmr.common.api :as api]
            [cmr.common.cache :as cache]
            [cmr.common.jobs :as jobs]
            [cmr.acl.core :as acl]
            [cheshire.core :as json]
            [compojure.core :refer :all]))

(def cache-api-routes
  "Create routes for the cache querying/management api"
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

(defn job-api-routes
  "Creates common routes for managing jobs such as pausing and resuming. The caller must have
  system ingest management update permission to call any of the jobs routes."
  ([]
   (job-api-routes nil))
  ([additional-job-routes]
   (context "/jobs" []
     ;; Pause all jobs
     (POST "/pause" {:keys [request-context params headers]}
       (let [context (acl/add-authentication-to-context request-context params headers)]
         (acl/verify-ingest-management-permission context :update)
         (jobs/pause-jobs (get-in context [:system :scheduler]))
         {:status 204}))

     ;; Resume all jobs
     (POST "/resume" {:keys [request-context params headers]}
       (let [context (acl/add-authentication-to-context request-context params headers)]
         (acl/verify-ingest-management-permission context :update)
         (jobs/resume-jobs (get-in context [:system :scheduler]))
         {:status 204}))

     ;; Retrieve status of jobs - whether they are paused or active
     (GET "/status" {:keys [request-context params headers]}
       (let [context (acl/add-authentication-to-context request-context params headers)]
         (acl/verify-ingest-management-permission context :update)
         (let [paused? (jobs/paused? (get-in context [:system :scheduler]))]
           {:status 200
            :body (json/generate-string {:paused paused?})})))

     additional-job-routes)))

(defn health-api-routes
  "Creates common routes for checking the health of a CMR application. Takes a health-fn which
  takes a request-context as a parameter to determine if the application and its dependencies are
  working as expected."
  [health-fn]
  (GET "/health" {request-context :request-context :as request}
    (let [pretty? (api/pretty-request? request)
          {:keys [ok? dependencies]} (health-fn request-context)]
      {:status (if ok? 200 503)
       :headers {"Content-Type" "application/json; charset=utf-8"}
       :body (json/generate-string dependencies {:pretty pretty?})})))