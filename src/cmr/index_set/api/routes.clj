(ns cmr.index-set.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [ring.util.response :as r]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [clojure.walk :as walk]
            [cmr.index-set.services.index-service :as index-svc]
            [cmr.system-trace.http :as http-trace]))

(defn- build-routes [system]
  (routes
    (POST "/index-sets" {body :body request-context :request-context params :params}
      (let [index-set (walk/keywordize-keys body)]
        (r/created (index-svc/create-index-set request-context index-set))))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



