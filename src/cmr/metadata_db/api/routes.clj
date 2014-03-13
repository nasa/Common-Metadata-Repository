(ns cmr.metadata-db.api.routes
  "Defines the HTTP URL routes for the application."
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
            [cmr.metadata-db.services.concept-services :as concept-services]))

;;; service proxies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- save-concept
  "Store a concept record and return the revision"
  [system concept]
  (let [revision-id (concept-services/save-concept system concept)]
    {:status 201
     :body {:revision-id revision-id}
     :headers {"Content-Type" "json"}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context "/concepts" []
             (POST "/" params
                   (save-concept system (:body params))))
    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





