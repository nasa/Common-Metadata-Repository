(ns cmr.indexer.indexer-util
  "Provide util functions for indexer functionality"
  (:require
    [cmr.common.log :as log :refer [info warn error]]))

;; TODO unit test
(defn es-cluster-name-str->keyword
  [es-cluster-name]
  (let [es-cluster-name-keyword (if (keyword? es-cluster-name)
                                  es-cluster-name
                                  (keyword es-cluster-name))]
    (if (or (= es-cluster-name-keyword (keyword cmr.elastic-utils.config/gran-elastic-name))
            (= es-cluster-name-keyword (keyword cmr.elastic-utils.config/non-gran-elastic-name)))
      es-cluster-name-keyword
      (throw (Exception. "Expected es-cluster-name to be gran-elastic or non-gran-elastic, but got some other value."))
      )))

;; TODO unit test
(defn context->es-store
  "Returns the elastic store object in the context.
  es-cluster-name can be 'non-gran-elastic' or 'gran-elastic'"
  [context es-cluster-name]
  (get-in context [:system (es-cluster-name-str->keyword es-cluster-name)]))

;; TODO unit test
(defn context->conn
  "Returns the elastisch connection in the context"
  ;([context]
  ;(get-in context [:system :db :conn])) ;; db is gran-elastic
  [context es-cluster-name]
  (info "10636- INSIDE context->conn, system :gran-elastic is " (get-in context [:system :gran-elastic]) " and system :non-gran-elastic is " (get-in context [:system :non-gran-elastic]) " and cluster name is " es-cluster-name)
  (get-in context [:system (es-cluster-name-str->keyword es-cluster-name) :conn]))

;; TODO this is hardcoded to index name...could it be better? Will these rules always be true?
;; TODO unit test this --  need a sys test as well, so that if any index is created or found, we will auto warn that something could break with this
(defn get-es-cluster-name-from-index-name
  [index-name]
  (info "10636- INSIDE get-es-cluster-from-index-name. Given index-name = " index-name)
  (if
    (and (not (= index-name "1_collections_v2"))
         (or (clojure.string/starts-with? index-name "1_c")
             (= index-name "1_small_collections")
             (= index-name "1_deleted_granules")
             (= index-name (str cmr.elastic-utils.config/gran-elastic-name "-index-sets"))))
    cmr.elastic-utils.config/gran-elastic-name
    cmr.elastic-utils.config/non-gran-elastic-name)
  )