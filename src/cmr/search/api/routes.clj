(ns cmr.search.api.routes
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.mime-types :as mt]
            [cmr.search.services.query-service :as query-svc]
            [cmr.system-trace.http :as http-trace]
            [cmr.search.services.search-results :as sr]
            [cmr.search.services.parameters.legacy-parameters :as lp]))

(defn- get-search-results-format
  "Returns the requested search results format parsed from headers"
  [headers]
  (let [mime-type (get headers "accept")]
    (mt/validate-request-mime-type mime-type sr/supported-mime-types)
    (mt/mime-type->format mime-type)))

(defn- measure-query-time
  "Executes the query function measuring how long it takes. Adds a :took key to the results map
  with the number of milliseconds taken."
  [query-fn]
  (let [start (System/currentTimeMillis)
        result (query-fn)
        stop (System/currentTimeMillis)
        took (- stop start)]
    (assoc result :took took)))

(defn- find-references
  "Invokes query service to find references and returns the response"
  [context concept-type params headers query-string]
  (let [result-format (get-search-results-format headers)
        params (assoc params :result-format result-format)
        _ (info (format "Searching for %ss in format %s with params %s." (name concept-type) result-format (pr-str params)))
        search-params (lp/process-legacy-psa params query-string)
        results (measure-query-time #(query-svc/find-concepts-by-parameters context concept-type search-params))]
    (info (format "Found %d %ss in %d ms in format %s with params %s."
                  (:hits results) (name concept-type) (:took results) result-format (pr-str params)))
    {:status 200
     :headers {"Content-Type" (str (mt/format->mime-type result-format) "; charset=utf-8")}
     :body (sr/search-results->response context results result-format pretty?)}))

(defn- find-concept-by-cmr-concept-id
  "Invokes query service to find concept metadata by cmr concept id and returns the response"
  [context concept-id headers]
  ;; TODO headers argument is reserved for ACL validation
  (info (format "Search for concept with cmr-concept-id [%s]" concept-id))
  (let [concept (query-svc/find-concept-by-id context concept-id)]
    {:status 200
     :headers {"Content-Type" "application/xml; charset=utf-8"}
     :body (:metadata concept)}))

(defn- build-routes [system]
  (routes
    (context (get-in system [:search-public-conf :relative-root-url]) []
      (context "/collections" []
        (GET "/" {params :params headers :headers context :request-context query-string :query-string}
          (find-references context :collection params headers query-string))
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-references context :collection params headers body)))
      (context "/granules" []
        (GET "/" {params :params headers :headers context :request-context query-string :query-string}
          (find-references context :granule params headers query-string))
        (POST "/" {params :params headers :headers context :request-context body :body-copy}
          (find-references context :granule params headers body)))
      (context "/concepts/:cmr-concept-id" [cmr-concept-id]
        (GET "/" {headers :headers context :request-context}
          (find-concept-by-cmr-concept-id context cmr-concept-id headers)))

      ;; reset operation available just for development purposes
      ;; clear the cache for search app
      (POST "/reset" {:keys [request-context]}
        (r/created (query-svc/reset request-context))))
    (route/not-found "Not Found")))

;; Copies the body into a new attributed called :body-copy so that after a post of form content type
;; the original body can still be read. The default ring params reads the body and parses it and we
;; don't have access to it.
(defn copy-of-body-handler
  [f]
  (fn [request]
    (let [^String body (slurp (:body request))]
      (f (assoc request
                :body-copy body
                :body (java.io.ByteArrayInputStream. (.getBytes body)))))))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      copy-of-body-handler
      ring-json/wrap-json-body
      ring-json/wrap-json-response))
