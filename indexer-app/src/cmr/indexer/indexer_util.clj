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
  (get-in context [:system :db :conn]))
  ([context es-cluster-name]
   (info "10636- INSIDE context->conn, context is " context " and cluster name is " es-cluster-name)
   ;; cluster name can be gran-elastic or non-gran-elastic
  (get-in context [:system :elastic (keyword es-cluster-name) :conn])))
