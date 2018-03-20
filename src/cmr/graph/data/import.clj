(ns cmr.graph.data.import
  "Functions for importing data into neo4j."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]))

(def json-collections-filename
  "data/all_public_collections_from_es.json")

(def test-file
  "data/testfile.json")

(def url-fields
  "List of fields we are interested in parsing from a given URL."
  [:type :url])

(def relevant-fields
  "List of fields to parse from a collection record."
  [:concept-id :provider-id :related-urls :data-center :version-id :metadata-format])

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

(comment
 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))
 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file)))))
