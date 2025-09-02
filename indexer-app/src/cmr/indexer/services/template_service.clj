(ns cmr.indexer.services.template-service
  "Provide functions to create index templates"
  (:require
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.es-index-helper :as es-index]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.indexer-util :as indexer-util]))

(def index-templates
  "Map of index settings we want made into index templates"
  {:granule_index_template {:settings index-set/granule-settings-for-individual-indexes
                            :mappings index-set/granule-mapping
                            :index-patterns ["1_c*"]}})

;; TODO 10636 fix me, understand where templates would go -- default to go to non-gran cluster for now, but this may have to be fine tuned
(defn- make-template
  "Send individual template map to be ingested to a specific elasticsearch cluster"
  [context index-template-key es-cluster-name]
  (es-index/create-index-template (indexer-util/context->conn context es-cluster-name)
                                  (name index-template-key)
                                  (index-template-key index-templates)))

(defn make-templates
  "Send template maps to be ingested to both elasticsearch clusters"
  [context]
  (doseq [index-template (keys index-templates)]
    (make-template context index-template es-config/gran-elastic-name)
    (make-template context index-template es-config/elastic-name)))
