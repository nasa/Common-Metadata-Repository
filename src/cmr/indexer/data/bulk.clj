(ns cmr.indexer.data.bulk
  "Implementation of elastisch bulk index preparation that supports _version and _version_type.
  The origninal implementation is in clojurewerkz.elastisch.rest.bulk namespace.")

(def ^:private special-operation-keys
  [:_index :_type :_version :_version_type :_id :_routing :_percolate :_parent :_timestamp :_ttl])

(defn index-operation
  "Takes a document map and extracts the keys/values related to indexing instructions and
  constructs a map that describes how to index the document. This map directly corresponds
  to the indexing maps in the Elasticsearch bulk API."
  [doc]
  {"index" (select-keys doc special-operation-keys)})

(defn bulk-index
  "generates the content for a bulk insert operation.  Elasticsearch's bulk operations take a
  series of lines of index information and documents to updates interleaved. This functions expects
  that the document will contain the index information. It extracts the index keys from each document
  and returns a sequence of index info, document, index info, document..., etc."
  ([documents]
     (let [operations (map index-operation documents)
           documents  (map #(apply dissoc % special-operation-keys) documents)]
       (interleave operations documents))))