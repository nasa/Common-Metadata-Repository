(ns cmr.graph.data.import
  "Functions for importing data into neo4j."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [cmr.graph.data.statement :as statement]))

(def json-collections-filename
  "data/all_public_collections_from_es.json")

(def test-file
  "data/testfile.json")

(def test-csv-file
  "data/collections.csv")

(def full-csv-file
  "data/all_collections.csv")

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
  ["ConceptId" "ProviderId" "DataCenter" "VersionId" "MetadataFormat"])

(defn collection->row
  "Returns a row to write to the collections CSV file for a given collection."
  [collection]
  (let [{:keys [provider-id concept-id data-center version-id metadata-format]} collection]
    [(first concept-id)
     (first provider-id)
     (first data-center)
     (first version-id)
     (first metadata-format)]))

(defn write-collection-csv
  "Creates the collection csv file"
  [collections output-filename]
  (with-open [csv-file (io/writer output-filename)]
    (csv/write-csv csv-file [collection-columns])
    (csv/write-csv csv-file (mapv collection->row collections))))


(comment
 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))
 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))

 (write-collection-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                       (str "resources/" full-csv-file))

 (write-collection-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
                       (str "resources/" csv-file))
 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
 (println
  (statement/neo4j-statements (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file)))))
  ))
