(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [cmr.common.log :as log :refer [info warn error]]
    [cmr.elastic-utils.config :as es-util-config]))


;; TODO 10636 unit test
(defn context->es-store
  "Returns the elastic store object in the context."
  [context es-cluster-name]
  (get-in context [:system (es-util-config/es-cluster-name-str->keyword es-cluster-name)]))

;; TODO 10636 unit test
(defn context->conn
  "Returns the elastisearch connection in the context"
  [context es-cluster-name]
  (info "10636- INSIDE context->conn, system :gran-elastic is " (get-in context [:system :gran-elastic]) " and system :elastic is " (get-in context [:system :elastic]) " and cluster name is " es-cluster-name)
  (get-in context [:system (es-util-config/es-cluster-name-str->keyword es-cluster-name) :conn]))