(ns cmr.access-control.data.bulk
  "Implementation of elastisch bulk index preparation that supports version and version_type.
  The original implementation is in clojurewerkz.elastisch.rest.bulk namespace.")

(def ^:private special-operation-keys
  [:_index :version :version_type :_id :_routing :_percolate :_parent :_timestamp :_ttl])

(defn index-operation
  "Takes a document map and extracts the keys/values related to indexing instructions and
  constructs a map that describes how to index the document. This map directly corresponds
  to the indexing maps in the Elasticsearch bulk API.

  If the document has a 'deleted' flag=true, a delete operation is created.
  This will delete the concept from elastic instead of add it."
  [doc]
  (if (:deleted doc)
   [{"delete" (select-keys doc special-operation-keys)}]
   [{"index" (select-keys doc special-operation-keys)}
    (apply dissoc doc special-operation-keys)]))

(defn create-bulk-index-operations
  "generates the content for a bulk insert operation.  Elasticsearch's bulk operations take a
  series of lines of index information and documents to updates interleaved. This functions expects
  that the document will contain the index information. It extracts the index keys from each document
  and returns a sequence of index info, document, index info, document..., etc."
  ([documents]
   (mapcat index-operation documents)))
