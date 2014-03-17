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

(defn- get-concept
  "Get a concept by concept-id and optional revision"
  [system concept-id revision]
  (let [revision-id (if revision (Integer. revision) nil)
        concept (concept-services/get-concept system concept-id revision-id)]
    {:status 200
     :body concept
     :headers json-header}))

(defn- get-concepts
  "Get concepts using concept-id/revision-id tuples."
  [system concept-id-revisions]
  (let [concepts (concept-services/get-concepts system concept-id-revisions)]
    {:status 200
     :body concepts
     :headers json-header}))

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

(defn- get-concept-id
  "Get the concept id for a given concept."
  [system concept]
  (let [concept-id (concept-services/get-concept-id system (clojure.walk/keywordize-keys concept))]
    {:status 200
     :body {:concept-id concept-id}
     :headers json-header}))
  

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context "/concepts" []
             ;; saves a concept
             (POST "/" params
                   (save-concept system (:body params)))
             ;; delete the entire database
             (DELETE "/" params
                   (force-delete system))
             ;; get a specific revision of a concept
             (GET "/:id/:revision" [id revision] (get-concept system id revision))
             ;; returns the latest revision of a concept
             (GET "/:id" [id] (get-concept system id nil))
             (POST "/search" params
                   (get-concepts system (get (:body params) "concept-revisions"))))
    
    (GET "/concept-id" params
         (get-concept-id params))
    
    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





