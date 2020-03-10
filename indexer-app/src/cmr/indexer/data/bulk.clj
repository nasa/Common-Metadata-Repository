(ns cmr.indexer.data.bulk
  "Implementation of elastisch bulk index preparation that supports version and version_type.
  The origninal implementation is in clojurewerkz.elastisch.rest.bulk namespace.")

(def ^:private special-operation-keys
  [:_id
   :_index
   :_parent
   :_percolate
   :_routing
   :_timestamp
   :_ttl
   :version
   :version_type])

(defn index-operation
  "Takes a document map and extracts the keys/values related to indexing instructions and
  constructs a map that describes how to index the document. This map directly corresponds
  to the indexing maps in the Elasticsearch bulk API.

  For the collection index if the document has a 'deleted' flag=true, a delete operation is created.
  This will delete the concept from elastic instead of add it."
  [doc]
  (if (:deleted doc)
   [{"delete" (select-keys doc special-operation-keys)}]
   [{"index" (select-keys doc special-operation-keys)}
    (apply dissoc doc special-operation-keys)]))

(defn index-operation-all-revisions
  "Takes a document map and extracts the keys/values related to indexing instructions and
  constructs a map that describes how to index the document. This map directly corresponds
  to the indexing maps in the Elasticsearch bulk API.

  For the all revisions index, if the concept has been deleted it is still added to the index with
  a field deleted:true.
  Purging the field from elastic will require a force-delete operation that is never done in bulk."
  [doc]
  [{"index" (select-keys doc special-operation-keys)}
   (apply dissoc doc special-operation-keys)])

(defn create-bulk-index-operations
  "generates the content for a bulk insert operation.  Elasticsearch's bulk operations take a
  series of lines of index information and documents to updates interleaved. This functions expects
  that the document will contain the index information. It extracts the index keys from each document
  and returns a sequence of index info, document, index info, document..., etc."
  ([documents]
   (create-bulk-index-operations documents false))
  ([documents all-revisions-index?]
   (if all-revisions-index?
     (mapcat index-operation-all-revisions documents)
     (mapcat index-operation documents))))
