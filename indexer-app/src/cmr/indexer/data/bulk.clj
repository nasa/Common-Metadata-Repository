(ns cmr.indexer.data.bulk
  "Implementation of elastisch bulk index preparation that supports _version and _version_type.
  The origninal implementation is in clojurewerkz.elastisch.rest.bulk namespace.")

(def ^:private special-operation-keys
  [:_index :_type :_version :_version_type :_id :_routing :_percolate :_parent :_timestamp :_ttl])

(defn index-operation
  "Takes a document map and extracts the keys/values related to indexing instructions and
  constructs a map that describes how to index the document. This map directly corresponds
  to the indexing maps in the Elasticsearch bulk API.

  If the document has a 'deleted' flag = true, then a delete operation is created instead."
  [doc]
  (if (and (:deleted doc) (not= "1_all_collection_revisions" (:_index doc)))
    [{"delete" (select-keys doc special-operation-keys)}]
    [{"index" (select-keys doc special-operation-keys)}
     (apply dissoc doc special-operation-keys)]))

(defn bulk-index
  "generates the content for a bulk insert operation.  Elasticsearch's bulk operations take a
  series of lines of index information and documents to updates interleaved. This functions expects
  that the document will contain the index information. It extracts the index keys from each document
  and returns a sequence of index info, document, index info, document..., etc."
  [documents]
  (mapcat index-operation documents))
