(ns cmr.graph.data.import
  "Functions for importing data into neo4j."
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojurewerkz.neocons.rest.cypher :as cypher]
   [cmr.graph.data.statement :as statement]
   [cmr.graph.data.tags :as tags]
   [digest :as digest]))

(def json-collections-filename
  "data/all_public_collections_from_es.json")

(def test-file
  "data/testfile.json")

(def collection-csv-file
  "data/collections.csv")

(def collection-url-csv-file
  "data/collection_and_urls.csv")

(def collection-data-center-csv-file
  "data/collection_and_data_centers.csv")

(def collection-tag-csv-file
  "data/collection_and_tags.csv")

(def url-fields
  "List of fields we are interested in parsing from a given URL."
  [:type :url])

(def relevant-fields
  "List of fields to parse from a collection record."
  [:concept-id :provider-id :entry-id :related-urls :data-center :version-id :metadata-format
   :tags-gzip-b64])

(defn parse-url-into-nodes
  "Parses a single URL field into all the nodes we want to create for the URL."
  [url]
  (select-keys (json/parse-string url true) url-fields))

(defn- parse-tags
  "Returns each of the tags and associated data from the provided Elasticsearch tags-gzip-b64
  field."
  [tags-gzip-b64]
  (when tags-gzip-b64
    (tags/gzip-base64-tag->edn tags-gzip-b64)))

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

(defn construct-collection-url-row
  "Creates a collection URL row for a relationship CSV file."
  [collection url]
  [(md5-leo (first (:concept-id collection)))
   (:url url)
   (:type url)])

(defn construct-collection-data-center-row
  "Creates a collection data center row for a relationship CSV file."
  [collection data-center]
  [(md5-leo (first (:concept-id collection)))
   data-center])

(defn write-collection-url-relationship-csv
  "Creates the collection<->url relationship csv file."
  [collections output-filename]
  (let [rows (doall
              (for [collection collections
                    url (:related-urls collection)]
                (construct-collection-url-row collection url)))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["CollectionMD5Leo" "URL" "URLType"]])
      (csv/write-csv csv-file rows))))

(defn write-collection-data-center-relationship-csv
  "Creates the collection<->data centers relationship csv file."
  [collections output-filename]
  (let [rows (doall
              (for [collection collections
                    data-center (:data-center collection)]
                (construct-collection-data-center-row collection data-center)))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["CollectionMD5Leo" "DataCenter"]])
      (csv/write-csv csv-file rows))))

(defn- construct-collection-tag-row
  "Creates a collection data center row for a relationship CSV file."
  [collection tag]
  (let [[tag-key tag-association-data] tag]
    [(md5-leo (first (:concept-id collection)))
     tag-key]))

(defn write-collection-tags-relationship-csv
  "Creates the collection<->tag relationship csv file."
  [collections output-filename]
  (let [rows (doall
              (for [collection collections
                    tag (parse-tags (first (:tags-gzip-b64 collection)))]
                (construct-collection-tag-row collection tag)))]
    (with-open [csv-file (io/writer output-filename)]
      (csv/write-csv csv-file [["CollectionMD5Leo" "TagKey"]])
      (csv/write-csv csv-file rows))))

(def import-statements
  "All of the import statements to run to populate a completely empty database. Make sure to
  delete everything before running."
  ["CREATE CONSTRAINT ON (url:Url) ASSERT url.name IS UNIQUE"
   "CREATE CONSTRAINT ON (urlType:UrlType) ASSERT urlType.name IS UNIQUE"
   "CREATE CONSTRAINT ON (coll:Collection) ASSERT coll.md5Leo IS UNIQUE"
   "CREATE CONSTRAINT ON (dataCenter:DataCenter) ASSERT dataCenter.name IS UNIQUE"
   "CREATE CONSTRAINT ON (tag:Tag) ASSERT tag.name IS UNIQUE"
   "LOAD CSV WITH HEADERS FROM \"https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collections.csv\" AS csvLine
      MERGE (format:MetadataFormat {name: csvLine.MetadataFormat})
      MERGE (version:Version {name: csvLine.VersionId})
      MERGE (provider:Provider {name: csvLine.ProviderId})
      CREATE (coll:Collection {md5Leo: csvLine.MD5Leo, conceptId: csvLine.ConceptId})
      CREATE (coll)-[:OWNED_BY]->(provider)
      CREATE (coll)-[:FORMATTED_IN]->(format)
      CREATE (coll)-[:VERSION_IS]->(version)"
   "USING PERIODIC COMMIT 500
      LOAD CSV WITH HEADERS FROM \"https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collection_and_urls.csv\" AS csvLine
      MATCH (coll:Collection { md5Leo: csvLine.CollectionMD5Leo})
      MERGE (url:Url { name: csvLine.URL})
      MERGE (urlType:UrlType { name: csvLine.URLType})
      CREATE (coll)-[:LINKS_TO]->(url)
      CREATE (url)-[:HAS_TYPE]->(urlType)"
   "USING PERIODIC COMMIT 500
      LOAD CSV WITH HEADERS FROM \"https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collection_and_data_centers.csv\" AS csvLine
      MATCH (coll:Collection { md5Leo: csvLine.CollectionMD5Leo})
      MERGE (dataCenter:DataCenter { name: csvLine.DataCenter})
      CREATE (coll)-[:AFFILIATED_WITH]->(dataCenter)"
   "USING PERIODIC COMMIT 500
      LOAD CSV WITH HEADERS FROM \"https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collection_and_tags.csv\" AS csvLine
      MATCH (coll:Collection { md5Leo: csvLine.CollectionMD5Leo})
      MERGE (tag:Tag { name: csvLine.TagKey})
      CREATE (coll)-[:TAGGED_WITH]->(tag)"])

(defn import-all-data
  "Imports all of the collection data."
  [conn]
  (doseq [statement import-statements]
    (cypher/tquery conn statement)))


(comment
 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))
 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
 (prepare-collection-for-import (first (:hits (:hits (read-json-file json-collections-filename)))))

 (write-collection-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                       (str "resources/" collection-csv-file))

 (write-collection-url-relationship-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                                        (str "resources/" collection-url-csv-file))

 (write-collection-data-center-relationship-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                                                (str "resources/" collection-data-center-csv-file))

 (write-collection-tags-relationship-csv (mapv prepare-collection-for-import (:hits (:hits (read-json-file json-collections-filename))))
                                         (str "resources/" collection-tag-csv-file))

 (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file))))
 (println
  (statement/neo4j-statements (mapv prepare-collection-for-import (:hits (:hits (read-json-file test-file)))))))
