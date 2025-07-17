(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [cmr.common.log :as log :refer [info warn error]]))

(defn context->es-store
  "Returns the elastic store object in the context"
  [context]
  (get-in context [:system :db]))

(defn context->conn
  "Returns the elastisch connection in the context"
  ([context]
  (get-in context [:system :db :conn])) ;; db is gran-elastic
  ([context es-cluster-name]
   (info "10636- INSIDE context->conn, system db is " (get-in context [:system :db]) " and system elastic is " (get-in context [:system :elastic]) " and cluster name is " es-cluster-name)
   ;; cluster name can be gran-elastic or non-gran-elastic
   (if (= es-cluster-name "non-gran-elastic")
     (get-in context [:system (keyword es-cluster-name) :conn])
     (get-in context [:system :db :conn])
     )
  ))
