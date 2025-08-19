(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [cmr.common.log :as log :refer [info warn error]]
    [cmr.elastic-utils.config]))


;; TODO 10636 unit test
(defn context->es-store
  "Returns the elastic store object in the context.
  es-cluster-name can be 'non-gran-elastic' or 'gran-elastic'"
  [context es-cluster-name]
  (get-in context [:system (cmr.elastic-utils.config/es-cluster-name-str->keyword es-cluster-name)]))

;; TODO 10636 unit test
(defn context->conn
  "Returns the elastisch connection in the context"
  ;([context]
  ;(get-in context [:system :db :conn])) ;; db is gran-elastic
  [context es-cluster-name]
  (info "10636- INSIDE context->conn, system :gran-elastic is " (get-in context [:system :gran-elastic]) " and system :non-gran-elastic is " (get-in context [:system :non-gran-elastic]) " and cluster name is " es-cluster-name)
  (get-in context [:system (cmr.elastic-utils.config/es-cluster-name-str->keyword es-cluster-name) :conn]))