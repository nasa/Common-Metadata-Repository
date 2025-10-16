(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [clojure.string :as string]
    [cmr.elastic-utils.config :as es-util-config]
    [cmr.indexer.data.index-set :as idx-set]))

(defn context->es-store
  "Returns the elastic store object in the context."
  [context es-cluster-name]
  (get-in context [:system (es-util-config/es-cluster-name-str->keyword es-cluster-name)]))

(defn context->conn
  "Returns the elastisearch connection in the context"
  [context es-cluster-name]
  (get-in context [:system (es-util-config/es-cluster-name-str->keyword es-cluster-name) :conn]))

(defn get-es-cluster-name-by-index-or-alias
  [es-index-or-alias]
  ;; if es-index-or-alias is granule type then get granule connection, else get the non-gran cluster connection
  (if
    (and (not (= es-index-or-alias (idx-set/collections-index)))
         (not (= es-index-or-alias (idx-set/collections-index-alias)))
         (or (string/starts-with? es-index-or-alias idx-set/granule-index-name-prefix)
             (= es-index-or-alias idx-set/small-collections-index-name)
             (= es-index-or-alias idx-set/small-collections-index-alias)
             (= es-index-or-alias idx-set/deleted-granule-index-name)
             (= es-index-or-alias idx-set/deleted-granules-index-alias)))
    es-util-config/gran-elastic-name
    es-util-config/elastic-name))
