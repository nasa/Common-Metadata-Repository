(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality")

(defn context->es-store
  "Returns the elastic store object in the context"
  [context]
  (get-in context [:system :db]))

(defn context->conn
  "Returns the elastisch connection in the context"
  ([context]
  (get-in context [:system :db :conn]))
  ([context es-cluster-name]
   ;; cluster name can be gran-elastic or non-gran-elastic
  (get-in context [:system :elastic es-cluster-name :conn])))
