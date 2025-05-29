(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
   [cmr.common.services.errors :as errors]))

(defn context->es-store
  "Returns the elastic store object in the context"
  [context]
  (get-in context [:system :db]))

(defn context->conn
  "Returns the elastisch connection in the context"
  [context]
  (get-in context [:system :db :conn]))
