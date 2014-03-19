(ns cmr-system-int-test.faux-ingest-util
  "Temporary namespace to ingest data directly into the metadata db and indexer"
  (:require [cmr-system-int-test.ingest-util :as ingest-util]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))



;;; Enpoints for services - change this for tcp-mon
(def metadata-db-port 4001)

(def mdb-concepts-url (str "http://localhost:" metadata-db-port "/concepts/"))

(def mdb-id-url (str "http://localhost:" metadata-db-port "/concept-id/"))

(def indexer-port 4004)

(def indexer-endpoint (str "http://localhost:" indexer-port "/"))

(defn get-concept-id
  "Make a GET the id for a given concept-type, provider-id, and native-id."
  [concept-type provider-id native-id]
  (let [response (client/get (str mdb-id-url (name concept-type) "/" provider-id "/" native-id)
                             {:accept :json})]
    (get (cheshire/parse-string (:body response)) "concept-id")))

(defn save-concept
  [concept]
  (let [response (client/post mdb-concepts-url
                              {:body (cheshire/generate-string concept)
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json})
        body (cheshire/parse-string (:body response))]
    (assoc concept :revision-id (get body "revision-id"))))

(defn delete-concept
  "Make a DELETE request to mark a concept as deleted. Returns the status and revision id of the
  tombstone."
  [concept-id]
  (let [response (client/delete (str mdb-concepts-url concept-id))
        body (cheshire/parse-string (:body response))]
    (get body "revision-id")))

(defn index-concept
  [concept]
  (let [{:keys [concept-id revision-id]} concept
        request {:concept-id concept-id
                 :revision-id revision-id}]
    (client/post indexer-endpoint
                 {:body (cheshire/generate-string request)
                  :body-encoding "UTF-8"
                  :content-type :json
                  :accept :json})))

(defn unindex-concept
  [concept-id revision-id]
  (let [delete-url (format "%s/%s/%s" indexer-endpoint concept-id revision-id)]
    (client/delete delete-url)))

(defn create-concept
  [provider-id collection]
  (let [collection (merge ingest-util/default-collection collection)
        collection-xml (ingest-util/metadata-xml collection)]
    {:concept-type :collection
     :native-id (:dataset-id collection)
     :concept-id (get-concept-id :collection provider-id (:dataset-id collection))
     :provider-id provider-id
     :metadata collection-xml
     :format "echo10"}))

(defn update-collection
  "Update collection (given or the default one) through CMR metadata API.
  TODO Returns cmr-collection id eventually"
  ([provider-id]
   (update-collection provider-id {}))
  ([provider-id collection]
   (let [concept (create-concept provider-id collection)
         concept (save-concept concept)]
     (index-concept concept))))

(defn delete-collection
  "Delete the collection with the matching native-id from the CMR metadata repo.
  native-id is equivalent to dataset id.
  I call it native-id because the id in the URL used by the provider-id does not have to be
  the dataset id in the collection in general even though catalog rest will enforce this."
  [provider-id native-id]
  (let [concept-id (get-concept-id :collection provider-id native-id)
        revision-id (delete-concept concept-id)]
    (unindex-concept concept-id revision-id)))
