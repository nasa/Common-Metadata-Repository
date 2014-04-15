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
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.provider-service :as provider-service]))

;;; service proxies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def json-header
  {"Content-Type" "json"})

(defn- get-concept
  "Get a concept by concept-id and optional revision"
  ([context concept-id]
   {:status 200
    :body (concept-service/get-concept context concept-id)
    :headers json-header})
  ([context concept-id ^String revision]
   (try (let [revision-id (if revision (Integer. revision) nil)]
          {:status 200
           :body (concept-service/get-concept context concept-id revision-id)
           :headers json-header})
     (catch NumberFormatException e
       (serv-err/throw-service-error :invalid-data (.getMessage e))))))

(defn- get-concepts
  "Get concepts using concept-id/revision-id tuples."
  [context concept-id-revisions]
  (let [concepts (concept-service/get-concepts context concept-id-revisions)]
    {:status 200
     :body concepts
     :headers json-header}))

(defn- save-concept
  "Store a concept record and return the revision"
  [context concept]
  (let [concept (-> concept
                    clojure.walk/keywordize-keys
                    (update-in [:concept-type] keyword))
        {:keys [concept-id revision-id]} (concept-service/save-concept context concept)]
    {:status 201
     :body {:revision-id revision-id :concept-id concept-id}
     :headers json-header}))

(defn- delete-concept
  "Mark a concept as deleted (create a tombstone)."
  [context concept-id revision-id]
  (try (let [revision-id (if revision-id (Integer. revision-id) nil)]
         (let [{:keys [revision-id]} (concept-service/delete-concept context concept-id revision-id)]
           {:status 200
            :body {:revision-id revision-id}
            :headers json-header}))
    (catch NumberFormatException e
      (serv-err/throw-service-error :invalid-data (.getMessage e)))))

(defn- force-delete
  "Permanently remove a concept version from the database."
  [context concept-id revision-id]
  (try (let [revision-id (Integer. revision-id)]
         (let [{:keys [revision-id]} (concept-service/force-delete context concept-id revision-id)]
           {:status 200
            :body {:revision-id revision-id}
            :headers json-header}))
    (catch NumberFormatException e
      (serv-err/throw-service-error :invalid-data (.getMessage e)))))

(defn- reset
  "Delete all concepts from the data store"
  [context]
  (concept-service/reset context)
  {:status 204
   :body nil
   :headers json-header})

(defn- get-concept-id
  "Get the concept id for a given concept."
  [context concept-type provider-id native-id]
  (let [concept-id (concept-service/get-concept-id context concept-type provider-id native-id)]
    {:status 200
     :body {:concept-id concept-id}
     :headers json-header}))

(defn- save-provider
  "Save a provider."
  [context provider-id]
  (let [saved-provider-id (provider-service/create-provider context provider-id)]
    {:status 201
     :body saved-provider-id
     :headers json-header}))

(defn- delete-provider
  "Delete a provider and all its concepts."
  [context provider-id]
  (provider-service/delete-provider context provider-id)
  {:status 200})

(defn- get-providers
  "Get a list of provider ids"
  [context]
  (let [providers (provider-service/get-providers context)]
    {:status 200
     :body {:providers providers}
     :headers json-header}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context "/concepts" []

      ;; saves a concept
      (POST "/" {:keys [request-context body]}
        (save-concept request-context body))
      ;; mark a concept as deleted (add a tombstone) specifying the revision the tombstone should have
      (DELETE "/:concept-id/:revision-id" {{:keys [concept-id revision-id]} :params request-context :request-context}
        (delete-concept request-context concept-id revision-id))
      ;; mark a concept as deleted (add a tombstone)
      (DELETE "/:concept-id" {{:keys [concept-id]} :params request-context :request-context}
        (delete-concept request-context concept-id nil))
      ;; remove a specific revision of a concpet form the database
      (DELETE "/force-delete/:concept-id/:revision-id" {{:keys [concept-id revision-id]} :params
                                                        request-context :request-context}
        (force-delete request-context concept-id revision-id))
      ;; get a specific revision of a concept
      (GET "/:concept-id/:revision-id" {{:keys [concept-id revision-id]} :params request-context :request-context}
        (get-concept request-context concept-id revision-id))
      ;; get the latest revision of a concept
      (GET "/:concept-id" {{:keys [concept-id]} :params request-context :request-context}
        (get-concept request-context concept-id))
      ;; get multiple concpts by concept-id and revision-id
      (POST "/search" {:keys [request-context body]}
        (get-concepts request-context (get body "concept-revisions"))))

    ;; get the concept id for a given concept-type, provider-id, and native-id
    (GET "/concept-id/:concept-type/:provider-id/:native-id"
      {{:keys [concept-type provider-id native-id]} :params request-context :request-context}
      (get-concept-id request-context concept-type provider-id native-id))

    (context "/providers" []
      ;; create a new provider
      (POST "/" {:keys [request-context body]}
        #_(throw (Exception. "stop"))
        (save-provider request-context (get body "provider-id")))
      ;; delete a provider
      (DELETE "/:provider-id" {{:keys [provider-id]} :params request-context :request-context}
        (delete-provider request-context provider-id))
      ;; get a list of providers
      (GET "/" {request-context :request-context}
        (get-providers request-context)))

    ;; delete the entire database
    (POST "/reset" {:keys [request-context]}
      (reset request-context))

    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





