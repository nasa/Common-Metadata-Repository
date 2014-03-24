(ns ^{:doc "provides ingest realted utilities."}
  cmr-system-int-test.ingest-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr-system-int-test.url-helper :as url]))

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
                              {:content-type :xml
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
(defn mdb-endpoint
  "Returns the host and port of metadata db"
  []
  {:host "localhost"
   :port "3001"})

(def cmr-valid-content-types
  #{"application/echo10+xml", "application/iso_prototype+xml", "application/iso:smap+xml",
    "application/iso19115+xml", "application/dif+xml"})

(def base-concept-attribs
  {:short-name "SN-Sedac88" 
   :version "Ver88"  
   :long-name "LongName Sedac88"
   :dataset-id "LarcDatasetId88"})

(defn distinct-concept
  "Generate a concept-type, provicer-id, native-id tuple"
  [token]
  (hash-map :concept-type :collection
            :provider-id (str "PROV" token)
            :native-id (str "nativeId" token)
            :metadata (metadata-xml base-concept-attribs)
            :format "echo10+xml"))

(defn construct-ingest-rest-url
  "Construct ingest url based on concept."
  [concept]
  (let [host "localhost"
        port 3002
        {:keys [provider-id concept-type native-id ]} concept  
        ctx-part (str "providers" "/" provider-id  "/" "collections" "/" native-id )
        ingest-rest-url (str "http://" host ":" port "/" ctx-part)]
    ingest-rest-url))

;;; operations
(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [concept]
  (let [response (client/request
                   {:method :put
                    :url (construct-ingest-rest-url concept) 
                    :body  (:metadata concept)
                    :content-type (:format concept)
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :response response}))

(defn delete-concept
  "Delete a given concept."
  [concept]
  (let [response (client/request
                   {:method :delete
                    :url (construct-ingest-rest-url concept)
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response)) 
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :response response}))

;; re-use of cmr.cmr-indexer.data.metadata-db/get-concept
(defn concept-exists?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (let [{:keys [host port]} (mdb-endpoint)
        response (client/get (format "http://%s:%s/concepts/%s/%s" host port concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= 200 status) true false)))

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (let [{:keys [host port]} (mdb-endpoint)
        response (client/delete (format "http://%s:%s/concepts/%s" host port "force-delete")
                                {:accept :json
                                 :throw-exceptions false})
        status (:status response)]
    status))

(comment 
  (str (str "http://localhost:" 3001 "/concepts/") "force-delete")
  (format "http://%s:%s/concepts/%s" "loc" 3001 "force-delete")
  (str (format "http://%s:%s/concepts " "loc" 3001)  "force-delete"))