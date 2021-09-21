(ns cmr.common-app.api.routes
  "Defines routes that are common across multiple applications."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common.api.context :as cxt]
   [cmr.common.cache :as cache]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.xml :as cx]
   [compojure.core :refer [context GET POST]]
   [ring.middleware.json :as ring-json]
   [ring.util.codec :as rc]
   [ring.util.response :as ring-resp]))

(def RESPONSE_REQUEST_ID_HEADER
  "The HTTP response header field containing the current request id."
  "CMR-Request-Id")

(def RESPONSE_X_REQUEST_ID_HEADER
  "The HTTP response header field containing the current request id."
  "X-Request-Id")

(def TOKEN_HEADER "echo-token")

(def CONTENT_TYPE_HEADER "Content-Type")

(def HITS_HEADER "CMR-Hits")

(def TIMED_OUT_HEADER "CMR-Timed-Out")

(def TOOK_HEADER "CMR-Took")

(def SCROLL_ID_HEADER "CMR-Scroll-Id")

(def SEARCH_AFTER_HEADER "CMR-Search-After")

(def CORS_ORIGIN_HEADER
  "This CORS header is to restrict access to the resource to be only from the defined origins,
  value of \"*\" means all request origins have access to the resource"
  "Access-Control-Allow-Origin")

(def CORS_METHODS_HEADER
  "This CORS header is to define the allowed access methods"
  "Access-Control-Allow-Methods")

(def CORS_CUSTOM_ALLOWED_HEADER
  "This CORS header is to define the allowed custom headers"
  "Access-Control-Allow-Headers")

(def CORS_CUSTOM_EXPOSED_HEADER
  "This CORS header is to define the exposed custom headers"
  "Access-Control-Expose-Headers")

(def CORS_MAX_AGE_HEADER
  "This CORS header is to define how long in seconds the response of the preflight request can be cached"
  "Access-Control-Max-Age")

(def RESPONSE_STRICT_TRANSPORT_SECURITY_HEADER "Strict-Transport-Security")

(def RESPONSE_X_CONTENT_TYPE_OPTIONS_HEADER "X-Content-Type-Options")

(def RESPONSE_X_FRAME_OPTIONS_HEADER "X-Frame-Options")

(def RESPONSE_X_XSS_PROTECTION_HEADER "X-XSS-Protection")

(defn- search-response-headers
  "Generate headers for search response. CORS response headers can be tested through
  dev-system/resources/cors_headers_test.html"
  [content-type results]
  (merge {CONTENT_TYPE_HEADER (mt/with-utf-8 content-type)
          CORS_CUSTOM_EXPOSED_HEADER "CMR-Hits, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id, CMR-Search-After, CMR-Timed-Out, CMR-Shapefile-Original-Point-Count, CMR-Shapefile-Simplified-Point-Count",
          CORS_ORIGIN_HEADER "*"}
         (when (:timed-out results) {TIMED_OUT_HEADER "true"})
         (when (:hits results) {HITS_HEADER (str (:hits results))})
         (when (:took results) {TOOK_HEADER (str (:took results))})
         (if (:scroll-id results)
           {SCROLL_ID_HEADER (str (:scroll-id results))}
           (when (:search-after results) {SEARCH_AFTER_HEADER (json/encode (:search-after results))}))))

(defn search-response
  "Generate the response map for finding concepts"
  [{:keys [results result-format] :as response}]
  {:status  200
   :headers (search-response-headers
             (if (string? result-format)
               result-format
               (mt/format->mime-type result-format))
             response)
   :body    results})

(defn options-response
  "Generate the response map when requesting options"
  []
  {:status 200
   :headers {CONTENT_TYPE_HEADER "text/plain; charset=utf-8"
             CORS_ORIGIN_HEADER "*"
             CORS_METHODS_HEADER "POST, GET, OPTIONS"
             CORS_CUSTOM_ALLOWED_HEADER (str (when (acl/allow-echo-token) "Echo-Token, ") "Accept, Content-Type, Client-Id, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id, CMR-Search-After, Authorization")
             ;; the value in seconds for how long the response to the preflight request can be cached
             ;; set to 30 days
             CORS_MAX_AGE_HEADER "2592000"}})

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
            ;; caches (get-in request-context [:system :caches])
            ;; _ (println "CACHES")
            ;; _ (clojure.pprint/pprint caches)
            ;; _ (println "GETTING CACHE " (keyword cache-name))
            cache (cache/context->cache request-context (keyword cache-name))
            ;; _ (when (nil? cache) (println "CACHE IS NIL"))
            result (cache/get-value cache cache-key)]
        (if result
          {:status 200
           :body (json/generate-string result)}
           {:status 404
            :body (json/generate-string
                   {:error (format "missing key [%s] for cache [%s]" cache-key cache-name)})})))

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

(defn pretty-request?
  "Returns true if the request indicates the response should be returned in a human readable
  fashion. This can be specified either through a pretty=true in the URL query parameters or
  through a Cmr-Pretty HTTP header."
  [request]
  (let [{:keys [headers params]} request]
    (or (= "true" (get params "pretty"))
        (= "true" (get headers "cmr-pretty")))))

(defn pretty-print-json
  [json-str]
  (-> json-str
      json/parse-string
      (json/generate-string {:pretty true})))

(defn update-response-body-with-fn
  [response f]
  (let [^String new-body (f (:body response))]
    (-> response
        (assoc-in [:body] new-body)
        (assoc-in [:headers "Content-Length"] (str (count (.getBytes new-body "UTF-8"))))
        (ring-resp/charset "UTF-8"))))

(defn- pretty-print-body
  "Update the body of the response to a pretty printed string based on the content type"
  [response]
  (let [mime-type (mt/content-type-mime-type (:headers response))
        find-re (fn [re] (and mime-type (re-find re mime-type)))]
    (if (string? (:body response))
      (cond
        (find-re #"application/.*json.*")
        (update-response-body-with-fn response pretty-print-json)

        (find-re #"application/.*xml.*")
        (update-response-body-with-fn response cx/pretty-print-xml)

        :else response)

      (update-response-body-with-fn response #(json/generate-string % {:pretty true})))))

(defn pretty-print-response-handler
  "Middleware which pretty prints the response if the parameter pretty in the
  URL query is set to true of if the header Cmr-Pretty is set to true."
  [handler]
  (fn [request]
    (let [pretty? (pretty-request? request)
          request (-> request
                      (update-in [:params] dissoc "pretty")
                      (update-in [:query-params] dissoc "pretty"))]
      (if pretty?
        (pretty-print-body (handler request))
        ((ring-json/wrap-json-response handler) request)))))

(defn add-request-id-response-handler
  "Adds a request id header to every response to facilitate clientside debugging."
  [handler]
  (fn [{context :request-context :as request}]
    (if-let [request-id (cxt/context->request-id context)]
      (-> request
          (handler)
          (assoc-in [:headers RESPONSE_REQUEST_ID_HEADER] request-id)
          (assoc-in [:headers RESPONSE_X_REQUEST_ID_HEADER] request-id))
      ((ring-json/wrap-json-response handler) request))))

(defn add-security-header-response-handler
  "Adds a number of security related response headers."
  [handler]
  (fn [{context :request-context :as request}]
    (if-let [request-id (cxt/context->request-id context)]
      (-> request
          (handler)
          (assoc-in [:headers RESPONSE_STRICT_TRANSPORT_SECURITY_HEADER] "max-age=31536000")
          (assoc-in [:headers RESPONSE_X_CONTENT_TYPE_OPTIONS_HEADER] "nosniff")
          (assoc-in [:headers RESPONSE_X_FRAME_OPTIONS_HEADER] "SAMEORIGIN")
          (assoc-in [:headers RESPONSE_X_XSS_PROTECTION_HEADER] "1; mode=block"))
      ((ring-json/wrap-json-response handler) request))))
