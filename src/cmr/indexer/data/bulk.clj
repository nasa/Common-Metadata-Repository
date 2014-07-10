(ns cmr.indexer.data.bulk
  "Implementation of elasitch bulk index preparation that supports _version and _version_type")

(def ^:private special-operation-keys
  [:_index :_type :_version :_version_type :_id :_routing :_percolate :_parent :_timestamp :_ttl])

(defn index-operation
  [doc]
  {"index" (select-keys doc special-operation-keys)})

(defn bulk-index
  "generates the content for a bulk insert operation"
  ([documents]
     (let [operations (map index-operation documents)
           documents  (map #(dissoc % :_index :_type :_version :_version_type) documents)]
       (interleave operations documents))))