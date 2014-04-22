(ns ^{:doc "provides ingest realted utilities."}
  cmr.system-int-test.utils.ingest-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.data.collection-helper :as ch]
            [cmr.system-int-test.data.granule-helper :as gh]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn create-provider
  "Create the provider with the given provider id"
  [provider-id]
  (let [response (client/post url/create-provider-url
                              {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})]
    (is (some #{201 200} [(:status response)]))))

(defn delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (let [response (client/delete (url/delete-provider-url provider-id)
                                {:throw-exceptions false})
        status (:status response)]
    (is (some #{200 404} [status]))))

(def default-collection {:short-name "MINIMAL"
                         :version-id "1"
                         :long-name "A minimal valid collection"
                         :entry-title "MinimalCollectionV1"})

(defn collection-xml
  "Returns metadata xml of the collection"
  [field-values]
  (echo10/umm->echo10-xml (ch/collection field-values)))

(defn granule-xml
  "Returns metadata xml of the granule"
  [field-values]
  (echo10/umm->echo10-xml (gh/granule field-values)))

(defn update-collection
  "Update collection (given or the default one) through CMR metadata API.
  TODO Returns cmr-collection id eventually"
  ([provider-id]
   (update-collection provider-id {}))
  ([provider-id collection]
   (let [full-collection (merge default-collection collection)
         collection-xml (collection-xml full-collection)
         response (client/put (url/collection-ingest-url provider-id (:entry-title full-collection))
                              {:content-type :echo10+xml
                               :body collection-xml
                               :throw-exceptions false})]
     (is (some #{201 200} [(:status response)])))))

(defn update-granule
  "Update granule (given or the default one) through CMR metadata API.
  TODO Returns cmr-granule id eventually"
  ([provider-id]
   (update-granule provider-id {}))
  ([provider-id granule]
   (let [granule-xml (granule-xml granule)
         response (client/put (url/granule-ingest-url provider-id (:granule-ur granule))
                              {:content-type :echo10+xml
                               :body granule-xml
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

(defn delete-granule
  "Delete the granule with the matching native-id from the CMR metadata repo.
  native-id is equivalent to dataset id.
  I call it native-id because the id in the URL used by the provider-id does not have to be
  the dataset id in the granule in general even though catalog rest will enforce this."
  [provider-id native-id]
  (let [response (client/delete (url/granule-ingest-url provider-id native-id)
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
   :version-id "Ver88"
   :entry-title "ABC"
   :long-name "LongName Sedac88"
   :dataset-id "LarcDatasetId88"})

(defn collection-concept
  "Creates a collection concept"
  [provider-id uniq-num]
  {:concept-type :collection
   :native-id (str "native-id " uniq-num)
   :provider-id provider-id
   :metadata (collection-xml base-concept-attribs)
   :content-type "application/echo10+xml"
   :deleted false
   :extra-fields {:short-name (str "short" uniq-num)
                  :version-id (str "V" uniq-num)
                  :entry-title (str "dataset" uniq-num)}})

(defn granule-concept
  "Creates a granule concept"
  [provider-id parent-collection-id uniq-num & concept-id]
  (let [granule {:concept-type :granule
                 :native-id (str "native-id " uniq-num)
                 :provider-id provider-id
                 :metadata (granule-xml base-concept-attribs)
                 :content-type "application/echo10+xml"
                 :deleted false
                 :extra-fields {:parent-collection-id parent-collection-id}}]
    (if concept-id
      (assoc granule :concept-id (first concept-id))
      granule)))

;;; operations
(defn ingest-collection
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


(defn ingest-granule
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [{:keys [metadata content-type concept-id parent-collection-id revision-id provider-id native-id] :as concept}]
  (let [response (client/request
                   {:method :put
                    :url (url/granule-ingest-url provider-id native-id)
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
  "Generate a concept and create the reqired provider in metadata db"
  [unique-id]
  (let [provider-id (str "PROV" unique-id)
        _ (create-provider provider-id)
        concept (hash-map :concept-type :collection
                          :provider-id provider-id
                          :native-id (str "nativeId" unique-id)
                          :metadata (collection-xml base-concept-attribs)
                          :content-type "application/echo10+xml")]
    (if memory-db?
      (assoc concept :concept-id (get-concept-id concept))
      concept)))

(defn distinct-concept-w-concept-id
  "Simulates a concept with provider supplied concept-id and create the provider in metadata db"
  [unique-id]
  (let [provider-id (str "PROV" unique-id)
        _ (create-provider provider-id)
        concept (hash-map :concept-type :collection
                          :provider-id provider-id
                          :native-id (str "nativeId" unique-id)
                          :metadata (collection-xml base-concept-attribs)
                          :content-type "application/echo10+xml")
        concept-id (format "C%s-%s" unique-id provider-id)]
    (assoc concept :concept-id concept-id)))

;; re-use of cmr.cmr-indexer.data.metadata-db/get-concept
(defn concept-exists-in-mdb?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (let [response (client/get (url/mdb-concept-url concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= 200 status) true false)))

(defn reset
  "Resets the database and the elastic indexes"
  []
  (client/post (url/mdb-reset-url))
  (client/post (url/indexer-reset-url)))


;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn each-fixture [f]
  (reset)
  (f)
  (reset))

(defn reset-fixture
  "Creates a database fixture function to reset the database after every test.
  Optionally accepts a list of provider-ids to create before the test"
  [& provider-ids]
  (fn [f]
    (try
      (reset)
      (doseq [pid provider-ids] (create-provider pid))
      (f)
      (finally
        (reset)))))
