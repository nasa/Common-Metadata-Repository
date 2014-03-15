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

(def json-header
  {"Content-Type" "json"})

(defn- save-concept
  "Store a concept record and return the revision"
  [system concept]
  (let [revision-id (concept-services/save-concept system (clojure.walk/keywordize-keys concept))]
    {:status 201
     :body {:revision-id revision-id}
     :headers json-header}))

(defn- force-delete
  "Delete all concepts from the data store"
  [system]
  (concept-services/force-delete system)
  {:status 204
   :body nil
   :headers json-header})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context "/concepts" []
             (POST "/" params
                   (save-concept system (:body params)))
             (DELETE "/" params
                   (force-delete system)))
    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





