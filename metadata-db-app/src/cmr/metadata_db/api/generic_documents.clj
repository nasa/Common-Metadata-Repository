(ns cmr.metadata-db.api.generic-documents
  "Defines the HTTP URL routes for Generic Documents"
  (:require
   [cmr.metadata-db.services.generic-documents :as gen-doc]
   [compojure.core :refer :all]))

(defn- create-generic-document
  "Create a Generic Document"
  [context params provider-id body]
  (let [result (gen-doc/insert-generic-document context params provider-id body)]
    {:status 200 :body result}))

(defn- read-generic-document
  "Read a Generic Document"
  [context params provider-id concept-id]
  (let [result (gen-doc/read-generic-document context params provider-id concept-id)]
    {:status 200 :body result}))

(defn- update-generic-document
  "Update a Generic Document"
  [context params provider-id concept-id body]
  (let [result (gen-doc/update-generic-document context params provider-id concept-id body)]
    {:status 204}))

(defn- delete-generic-document
  "Mark a document as deleted by creating a new tombstone revision"
  [context params provider-id concept-id]
  (let [result (gen-doc/delete-generic-document context params concept-id)]
    {:status 204}))

(def generic-document-api-routes
  (context "/generics" []
    
    ;{:keys [provider-id body] :as params} :params
    (POST "/:provider-id" {{:keys [provider-id] :as params} :params
                           body :body
                           request-context :request-context}
      (create-generic-document request-context params provider-id body))

    (GET "/:concept-id" {{:keys [concept-id] :as params} :params
                                      request-context :request-context}
      (read-generic-document request-context params "PROV1" concept-id))

    (PUT "/:provider-id/:concept-id" {{:keys [provider-id concept-id] :as params} :params
                                      body :body
                                      request-context :request-context}
      (update-generic-document request-context params provider-id concept-id body))
    
    (DELETE "/:provider-id/:concept-id" {{:keys [provider-id concept-id] :as params} :params
                                      request-context :request-context}
      (delete-generic-document request-context params provider-id concept-id))
    ))
