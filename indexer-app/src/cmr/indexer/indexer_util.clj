(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [cmr.common.log :as log :refer [info warn error]]
    [cmr.elastic-utils.config :as es-util-config]))


(defn context->es-store
  "Returns the elastic store object in the context."
  [context es-cluster-name]
  (get-in context [:system (es-util-config/es-cluster-name-str->keyword es-cluster-name)]))

(defn context->conn
  "Returns the elastisearch connection in the context"
  [context es-cluster-name]
  (get-in context [:system (es-util-config/es-cluster-name-str->keyword es-cluster-name) :conn]))