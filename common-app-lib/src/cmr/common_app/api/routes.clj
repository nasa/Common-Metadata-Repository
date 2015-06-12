(ns cmr.common-app.api.routes
  "Defines routes that are common across multiple applications."
  (:require [cmr.common.cache :as cache]
            [cmr.common.jobs :as jobs]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.acl.core :as acl]
            [cheshire.core :as json]
            [cmr.common.xml :as cx]
            [cmr.common.mime-types :as mt]
            [compojure.core :refer :all]
            [ring.util.codec :as rc]))

(def cache-api-routes
  "Create routes for the cache querying/management api"
  (context "/caches" []
    ;; Get the list of caches
    (GET "/" {:keys [params request-context headers]}
      (acl/verify-ingest-management-permission request-context :read)
      (let [caches (map name (keys (get-in request-context [:system :caches])))]
        {:status 200
         :body (json/generate-string caches)}))
    ;; Get the keys for the given cache
    (GET "/:cache-name" {{:keys [cache-name] :as params} :params
                         request-context :request-context
                         headers :headers}
      (acl/verify-ingest-management-permission request-context :read)
      (let [cache (cache/context->cache request-context (keyword cache-name))]
        (when cache
          (let [result (cache/get-keys cache)]
            {:status 200
             :body (json/generate-string result)}))))

    ;; Get the value for the given key for the given cache
    (GET "/:cache-name/:cache-key" {{:keys [cache-name cache-key] :as params} :params
                                    request-context :request-context
                                    headers :headers}
      (acl/verify-ingest-management-permission request-context :read)
      (let [cache-key (keyword cache-key)
            cache (cache/context->cache request-context (keyword cache-name))
            result (cache/get-value cache cache-key)]
        (when result
          {:status 200
           :body (json/generate-string result)})))

    (POST "/clear-cache" {:keys [request-context params headers]}
      (acl/verify-ingest-management-permission request-context :update)
      (cache/reset-caches request-context)
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
       (acl/verify-ingest-management-permission request-context :update)
       (jobs/pause-jobs (get-in request-context [:system :scheduler]))
       {:status 204})

     ;; Resume all jobs
     (POST "/resume" {:keys [request-context params headers]}
       (acl/verify-ingest-management-permission request-context :update)
       (jobs/resume-jobs (get-in request-context [:system :scheduler]))
       {:status 204})

     ;; Retrieve status of jobs - whether they are paused or active
     (GET "/status" {:keys [request-context params headers]}
       (acl/verify-ingest-management-permission request-context :update)
       (let [paused? (jobs/paused? (get-in request-context [:system :scheduler]))]
         {:status 200
          :body (json/generate-string {:paused paused?})}))

     additional-job-routes)))

(defn health-api-routes
  "Creates common routes for checking the health of a CMR application. Takes a health-fn which
  takes a request-context as a parameter to determine if the application and its dependencies are
  working as expected."
  [health-fn]
  (GET "/health" {request-context :request-context :as request}
    (let [{:keys [ok? dependencies]} (health-fn request-context)]
      (when-not ok?
        (warn "Health check failed" (pr-str dependencies)))
      {:status (if ok? 200 503)
       :headers {"Content-Type" (mt/with-utf-8 mt/json)}
       :body (json/generate-string dependencies)})))

(defn pretty-request?
  "Returns true if the request indicates the response should be returned in a human readable
  fashion. This can be specified either through a pretty=true in the URL query parameters or
  through a Cmr-Pretty HTTP header."
  ([request]
   (let [{:keys [headers params]} request]
     (or
       (= "true" (get params "pretty"))
       (= "true" (get headers "cmr-pretty"))))))

(defn- pretty-print-body
  "Update the body of the response to a pretty printed string based on the content type"
  [response]
  (let [mime-type (mt/mime-type-from-headers (:headers response))
        find-re (fn [re] (and mime-type (re-find re mime-type)))]
    (if (string? (:body response))
      (cond
        (find-re #"application/.*json.*") (update-in response [:body]
                                                     (fn [json-str]
                                                       (-> json-str
                                                           json/parse-string
                                                           (json/generate-string {:pretty true}))))
        (find-re #"application/.*xml.*") (update-in response [:body] cx/pretty-print-xml)
        :else response)
      ((ring-json/wrap-json-response identity {:pretty true}) response))))

(defn pretty-print-response-handler
  "Middleware which pretty prints the response if the parameter pretty in the
  URL query is set to true of if the header Cmr-Pretty is set to true."
  [f]
  (fn [request]
    (let [pretty? (pretty-request? request)
          request (-> request
                      (update-in [:params] dissoc "pretty")
                      (update-in [:query-params] dissoc "pretty"))]
      (if pretty?
        (pretty-print-body (f request))
        ((ring-json/wrap-json-response f) request)))))
