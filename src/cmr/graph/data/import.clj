(ns cmr.graph.data.import
  "Functions for importing data into neo4j."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [cmr.graph.data.statement :as statement]
   [digest :as digest]))

(def json-collections-filename
  "data/all_public_collections_from_es.json")

(def test-file
  "data/testfile.json")

(def collection-csv-file
  "data/collections.csv")

(def url-csv-file
  "data/urls.csv")

(def url-types-csv-file
  "data/url_types.csv")

(def providers-csv-file
  "data/providers.csv")

(def formats-csv-file
  "data/formats.csv")

(def data-centers-csv-file
  "data/data_centers.csv")

(def version-ids-csv-file
  "data/version_ids.csv")

(def url-fields
  "List of fields we are interested in parsing from a given URL."
  [:type :url])

(def relevant-fields
  "List of fields to parse from a collection record."
  [:concept-id :provider-id :entry-id :related-urls :data-center :version-id :metadata-format])

(defn parse-url-into-nodes
  "Parses a single URL field into all the nodes we want to create for the URL."
  [url]
  (select-keys (json/parse-string url true) url-fields))

(defn md5-leo
  "When a hash just isn't good enough."
  [value]
  (str "A" (digest/md5 value)))

(defn prepare-collection-for-import
  "Returns only the relevant JSON fields from the provided collection record for import into neo4j."
  [collection]
  (update (select-keys (:fields collection) relevant-fields)
          :related-urls
          (fn [urls]
            (mapv parse-url-into-nodes urls))))

(defn read-json-file
  "Reads a JSON file into memory"
  [filename]
  (json/parse-string (slurp (io/resource filename)) true))

(def collection-columns
  "Columns in the collections CSV file."
  ["MD5Leo" "ConceptId" "ProviderId" "VersionId" "MetadataFormat"])

(defn collection->row
  "Returns a row to write to the collections CSV file for a given collection."
  [collection]
  (let [{:keys [provider-id concept-id version-id metadata-format]} collection]
    [(md5-leo (first concept-id))
     (first concept-id)
     (first provider-id)
     (first version-id)
     (first metadata-format)]))

(defn write-collection-csv
  "Creates the collection csv file"
  [collections output-filename]
  (with-open [csv-file (io/writer output-filename)]
    (csv/write-csv csv-file [collection-columns])
    (csv/write-csv csv-file (mapv collection->row collections))))

(defn write-url-csv
  "Creates the URL csv file"
  [collections output-filename]
  (let [unique-urls (->> (mapcat :related-urls collections)
                         (keep :url)
                         set)]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["MD5Leo" "Url"]])
      (csv/write-csv csv-file (mapv (fn [url]
                                      [(md5-leo url) url])
                                    unique-urls)))))

(defn write-url-type-csv
  "Creates the URL types csv file"
  [collections output-filename]
  (let [unique-url-types (->> (mapcat :related-urls collections)
                              (keep :type)
                              set)]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["MD5Leo" "UrlType"]])
      (csv/write-csv csv-file (mapv (fn [url-type]
                                      [(md5-leo url-type) url-type])
                                    unique-url-types)))))

(defn write-providers-csv
  "Creates the providers csv file"
  [collections output-filename]
  (let [providers (set (mapcat :provider-id collections))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["MD5Leo" "ProviderId"]])
      (csv/write-csv csv-file (mapv (fn [provider]
                                      [(md5-leo provider) provider])
                                    providers)))))

(defn write-formats-csv
  "Creates the metadata formats csv file"
  [collections output-filename]
  (let [metadata-formats (set (mapcat :metadata-format collections))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["MD5Leo" "MetadataFormat"]])
      (csv/write-csv csv-file (mapv (fn [metadata-format]
                                      [(md5-leo metadata-format) metadata-format])
                                    metadata-formats)))))

(defn write-data-centers-csv
  "Creates the Data Centers csv file"
  [collections output-filename]
  (let [data-centers (set (mapcat :data-center collections))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["MD5Leo" "DataCenter"]])
      (csv/write-csv csv-file (mapv (fn [data-center]
                                      [(md5-leo data-center) data-center])
                                    data-centers)))))

(defn write-version-ids-csv
  "Creates the version IDs csv file"
  [collections output-filename]
  (let [version-ids (set (mapcat :version-id collections))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["MD5Leo" "VersionId"]])
      (csv/write-csv csv-file (mapv (fn [version-id]
                                      [(md5-leo version-id) version-id])
                                    version-ids)))))

(comment


 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))
 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))

 (write-collection-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                       (str "resources/" collection-csv-file))

 (write-url-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                (str "resources/" url-csv-file))

 (write-url-type-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                     (str "resources/" url-types-csv-file))
 (write-providers-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                      (str "resources/" providers-csv-file))

 (write-formats-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                    (str "resources/" formats-csv-file))
 (write-data-centers-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                         (str "resources/" data-centers-csv-file))

 (write-version-ids-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                        (str "resources/" version-ids-csv-file))

 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
 (println
  (statement/neo4j-statements (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file)))))))
