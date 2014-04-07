(ns ^{:doc "provides ingest realted utilities."}
  cmr.system-int-test.ingest-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.url-helper :as url]))

(def dummy-metadata
  "<Collection>
  <ShortName>DummyShortName</ShortName>
  <VersionId>DummyVersion</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <LongName>DummyLongName</LongName>
  <DataSetId>DummyDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
  </Collection>")

(def default-collection {:short-name "MINIMAL"
                         :version "1"
                         :long-name "A minimal valid collection"
                         :dataset-id "MinimalCollectionV1"})

(defn metadata-xml
  "Returns metadata xml of the collection"
  [{:keys [short-name version long-name dataset-id]}]
  (-> dummy-metadata
      (str/replace #"DummyShortName" short-name)
      (str/replace #"DummyVersion" version)
      (str/replace #"DummyLongName" long-name)
      (str/replace #"DummyDatasetId" dataset-id)))

(defn update-collection
  "Update collection (given or the default one) through CMR metadata API.
  TODO Returns cmr-collection id eventually"
  ([provider-id]
   (update-collection provider-id {}))
  ([provider-id collection]
   (let [full-collection (merge default-collection collection)
         collection-xml (metadata-xml full-collection)
         response (client/put (url/collection-ingest-url provider-id (:dataset-id full-collection))
                              {:content-type :echo10+xml
                               :body collection-xml
                               :throw-exceptions false})]
     (is (some #{201 200} [(:status response)])))))

(defn delete-collection
  "Delete the collection with the matching native-id from the CMR metadata repo.
  native-id is equivalent to dataset id.
  I call it native-id because the id in the URL used by the provider-id does not have to be
  the dataset id in the collection in general even though catalog rest will enforce this."
  [provider-id native-id]
  (let [response (client/delete (url/collection-ingest-url provider-id native-id)
                                {:throw-exceptions false})
        status (:status response)]
    (is (some #{200 404} [status]))))

;;; data/functions used in concept_ingest_test.clj
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cmr-valid-content-types
  #{"application/echo10+xml", "application/iso_prototype+xml", "application/iso:smap+xml",
    "application/iso19115+xml", "application/dif+xml"})

(def base-concept-attribs
  {:short-name "SN-Sedac88"
   :version "Ver88"
   :long-name "LongName Sedac88"
   :dataset-id "LarcDatasetId88"})

;;; operations
;;; make this generic to be applicable to other concept types
(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [{:keys [metadata content-type concept-id revision-id provider-id native-id] :as concept}]
  (let [response (client/request
                   {:method :put
                    :url (url/collection-ingest-url provider-id native-id)
                    :body  metadata
                    :content-type content-type
                    :headers {"concept-id" concept-id, "revision-id"  revision-id}
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :errors-str errors-str :response response}))

(defn delete-concept
  "Delete a given concept."
  [{:keys [provider-id native-id] :as concept}]
  (let [response (client/request
                   {:method :delete
                    :url (url/collection-ingest-url provider-id native-id)
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response))
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :response response}))

;; discard this once oracle impl is in place
(defn get-concept-id
  "Get a concept-id from memory db before save concept."
  [{:keys [provider-id native-id] :as concept}]
  (let [response (client/get (url/mdb-concept-coll-id-url provider-id native-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (when-not (= 200 status)
      (println (str "Concept id fetch failed. MetadataDb app response status code: "  status (str response))))
    (get (cheshire/parse-string (:body response)) "concept-id")))

;; set this to false when oracle impl is available
;; memory db requires concept id for save oper; hence get and set concept id before save concept oper.
(def memory-db? false)

(defn distinct-concept
  "Generate a concept"
  [unique-id]
  (let [concept (hash-map :concept-type :collection
                          :provider-id (str "PROV" unique-id)
                          :native-id (str "nativeId" unique-id)
                          :metadata (metadata-xml base-concept-attribs)
                          :content-type "application/echo10+xml")]
    (if memory-db?
      (assoc concept :concept-id (get-concept-id concept))
      concept)))

(defn distinct-concept-w-concept-id
  "Simulates a concept with provider supplied concept-id"
  [unique-id]
  (let [provider-id (str "PROV" unique-id)
        concept (hash-map :concept-type :collection
                          :provider-id provider-id
                          :native-id (str "nativeId" unique-id)
                          :metadata (metadata-xml base-concept-attribs)
                          :content-type "application/echo10+xml")
        concept-id (format "C%s-%s" unique-id provider-id)]
    (assoc concept :concept-id concept-id)))

;; re-use of cmr.cmr-indexer.data.metadata-db/get-concept
(defn concept-exists-in-mdb?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (let [response (client/get  (url/mdb-concept-url concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= 200 status) true false)))

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (let [response (client/post (url/mdb-reset-url)
                                {:accept :json
                                 :throw-exceptions false})
        status (:status response)]
    status))

(defn reset-es-indexes
  "Reset elastic indexes."
  []
  (let [response (client/post  (url/indexer-reset-url)
                              {:accept :json
                               :throw-exceptions false})
        status (:status response)]
    status))
