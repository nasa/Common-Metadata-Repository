(ns cmr.indexer.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.walk :as walk]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]

            ;; These must be required here to make multimethod implementations available.
            [cmr.indexer.data.concepts.collection]
            [cmr.indexer.data.concepts.granule]

            [cmr.indexer.services.index-service :as index-svc]
            [cmr.system-trace.http :as http-trace]))

(defn- ignore-conflict?
  "Return false if ignore_conflict parameter is set to false; otherwise return true"
  [params]
  (if (= "false" (:ignore_conflict params))
    false
    true))

(defn- build-routes [system]
  (routes

    (POST "/" {body :body request-context :request-context params :params}
      (let [{:keys [concept-id revision-id]} (walk/keywordize-keys body)
            ignore-conflict (ignore-conflict? params)]
        (r/created (index-svc/index-concept request-context concept-id revision-id ignore-conflict))))

    ;; reset operation available just for development purposes
    ;; delete configured elastic indexes and create them back
    (POST "/reset" {:keys [request-context]}
      (index-svc/reset-indexes request-context)
      {:status 200})

    (context "/:concept-id/:revision-id" [concept-id revision-id]
      (DELETE "/" {request-context :request-context params :params}
        (let [ignore-conflict (ignore-conflict? params)]
          (index-svc/delete-concept request-context concept-id revision-id ignore-conflict)
          (r/response nil))))

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



