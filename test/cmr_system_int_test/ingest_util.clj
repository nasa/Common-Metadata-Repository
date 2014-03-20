(ns ^{:doc "provides ingest realted utilities."}
  cmr-system-int-test.ingest-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
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
