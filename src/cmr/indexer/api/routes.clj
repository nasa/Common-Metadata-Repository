(ns cmr.indexer.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.indexer.services.index-service :as index-svc]
            [cmr.system-trace.http :as http-trace]))

(defn- build-routes [system]
  (routes

    (POST "/" {body :body request-context :request-context}
      (let [{:strs [concept-id revision-id]} body]
        (r/created (index-svc/index-concept request-context concept-id revision-id))))

    (context "/:concept-id/:revision-id" [concept-id revision-id]
      (DELETE "/" {request-context :request-context}
        (index-svc/delete-concept request-context concept-id revision-id)
        (r/response nil)))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



