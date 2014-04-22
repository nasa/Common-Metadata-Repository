(ns cmr.search.api.routes
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.search.services.query-service :as query-svc]
            [cmr.system-trace.http :as http-trace]
            [cmr.search.api.search-results :as sr]))

(defn- get-search-results-format
  "Returns the requested search results format parsed from headers"
  [headers]
  (let [mime-type (get headers "accept")]
    (sr/validate-search-result-mime-type mime-type)
    (sr/mime-type->format mime-type)))

(defn- find-references
  "Invokes query service to find references and returns the response"
  [context concept-type params headers]
  (let [result-format (get-search-results-format headers)
        pretty? (= (get params :pretty) "true")
        _ (info (format "Search for %ss in format [%s] with params [%s]" (name concept-type) result-format params))
        params (dissoc params :pretty)
        results (query-svc/find-concepts-by-parameters context concept-type params)]
    {:status 200
     :headers {"Content-Type" (sr/format->mime-type result-format)}
     :body (sr/search-results->response results result-format pretty?)}))

(defn- build-routes [system]
  (routes
    (context "/collections" []
      (GET "/" {params :params headers :headers context :request-context}
        (find-references context :collection params headers)))
    (context "/granules" []
      (GET "/" {params :params headers :headers context :request-context}
        (find-references context :granule params headers)))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))
