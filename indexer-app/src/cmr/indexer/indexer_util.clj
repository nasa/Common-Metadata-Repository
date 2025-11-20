(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [clojure.string :as string]
    [cmr.common.services.errors :as errors]
    [cmr.elastic-utils.config :as es-util-config]
    [cmr.indexer.data.index-set :as idx-set]))

(defn context->es-store
  "Returns the elastic store object in the context."
  [context es-cluster-name]
  (get-in context [:system (es-util-config/elastic-name-str->keyword es-cluster-name)]))

(defn context->conn
  "Returns the elastisearch connection in the context"
  [context es-cluster-name]
  (get-in context [:system (es-util-config/elastic-name-str->keyword es-cluster-name) :conn]))

;; IMPORTANT: If the rules of index naming ever changes, this func will need to change too
(defn get-es-cluster-name-by-index-or-alias
  [es-index-or-alias]
  ;; guard against invalid input
  (if (or (nil? es-index-or-alias)
          (not (string? es-index-or-alias))
          (string/blank? es-index-or-alias))
    (errors/throw-service-error
     :internal-error
     (format "Nil, non-string or blank elastic index or alias of %s is not allowed when determining elastic cluster name." es-index-or-alias)))
  ;; if es-index-or-alias is granule type then get granule connection, else get the non-gran cluster connection
  ;; determining granule indexes by 'startswith' because when resharding, index names will be changed and use the name of index as a prefix
  (if
    (and (not (= es-index-or-alias (idx-set/collections-index)))
         (not (= es-index-or-alias (idx-set/collections-index-alias)))
         (or (string/starts-with? es-index-or-alias idx-set/granule-index-name-prefix)
             (string/starts-with? es-index-or-alias idx-set/small-collections-index-name)
             (string/starts-with? es-index-or-alias idx-set/small-collections-index-alias)
             (string/starts-with? es-index-or-alias idx-set/deleted-granule-index-name)
             (string/starts-with? es-index-or-alias idx-set/deleted-granules-index-alias)))
    es-util-config/gran-elastic-name
    es-util-config/elastic-name))
