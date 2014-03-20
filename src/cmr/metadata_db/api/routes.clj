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
            [cmr.common.services.errors :as serv-err]
            [cmr.system-trace.http :as http-trace]
            [cmr.metadata-db.services.concept-services :as concept-services]))

;;; service proxies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def json-header
  {"Content-Type" "json"})

(defn- get-concept
  "Get a concept by concept-id and optional revision"
  [context concept-id ^String revision]
  (try (let [revision-id (if revision (Integer. revision) nil)
             concept (concept-services/get-concept context concept-id revision-id)]
         {:status 200
          :body concept
          :headers json-header})
    (catch NumberFormatException e
      (serv-err/throw-service-error :invalid-data (.getMessage e)))))

(defn- get-concepts
  "Get concepts using concept-id/revision-id tuples."
  [context concept-id-revisions]
  (let [concepts (concept-services/get-concepts context concept-id-revisions)]
    {:status 200
     :body concepts
     :headers json-header}))

(defn- save-concept
  "Store a concept record and return the revision"
  [context concept]
  (let [revision-id (concept-services/save-concept context (clojure.walk/keywordize-keys concept))]
    {:status 201
     :body {:revision-id revision-id}
     :headers json-header}))

(defn- delete-concept
  "Mark a concept as deleted (create a tombstone)."
  [context concept-id]
  (let [revision-id (concept-services/delete-concept context concept-id)]
    {:status 200
     :body {:revision-id revision-id}
     :headers json-header}))

(defn- force-delete
  "Delete all concepts from the data store"
  [context]
  (concept-services/force-delete context)
  {:status 204
   :body nil
   :headers json-header})

(defn- get-concept-id
  "Get the concept id for a given concept."
  [context concept-type provider-id native-id]
  (let [concept-id (concept-services/get-concept-id context concept-type provider-id native-id)]
    {:status 200
     :body {:concept-id concept-id}
     :headers json-header}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context "/concepts" []

      ;; saves a concept
      (POST "/" {:keys [request-context body]}
        (save-concept request-context body))

      ;; delete the entire database
      (DELETE "/force-delete" {:keys [request-context]}
        (force-delete request-context))

      (DELETE "/:id" {{id :id} :params request-context :request-context}
        (delete-concept request-context id))

      ;; get a specific revision of a concept
      (GET "/:id/:revision" {{:keys [id revision]} :params request-context :request-context}
        (get-concept request-context id revision))

      ;; returns the latest revision of a concept
      (GET "/:id" {{id :id} :params request-context :request-context}
        (get-concept request-context id nil))

      (POST "/search" {:keys [request-context body]}
        (get-concepts request-context (get body "concept-revisions"))))

    (GET "/concept-id/:concept-type/:provider-id/:native-id"
      {{:keys [concept-type provider-id native-id]} :params request-context :request-context}
      (get-concept-id request-context concept-type provider-id native-id))

    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





