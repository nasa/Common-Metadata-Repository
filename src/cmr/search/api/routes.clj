(ns cmr.search.api.routes
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cheshire.core :as json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.search.services.query-service :as query-svc]
            [cmr.system-trace.context :as context]))

(defn- build-routes [system]
  (routes
    (context "/collections" []
      (GET "/" {params :params headers :headers}
        (debug "Searching for collection with params" (pr-str params))
        (let [context (context/build-request-context system)
              results (query-svc/find-concepts-by-parameters context :collection params)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body results})))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



