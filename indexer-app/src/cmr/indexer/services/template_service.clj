(ns cmr.indexer.services.template-service
  "Provide functions to create index templates"
  (:require
   [cmr.elastic-utils.es-index-helper :as es-index]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.indexer-util :as indexer-util]))

(def index-templates
  "Map of index settings we want made into index templates"
  {:granule_index_template {:settings index-set/granule-settings-for-individual-indexes
                            :mappings index-set/granule-mapping
                            :index-patterns ["1_c*"]}})

(defn- make-template
  "Send individual template map to be ingested to elasticsearch"
  [context index-template-key]
  (es-index/create-index-template (indexer-util/context->conn context)
                                  (name index-template-key)
                                  (index-template-key index-templates)))

(defn make-templates
  "Send template maps to be ingested to elasticsearch"
  [context]
  (doseq [index-template (keys index-templates)]
    (make-template context index-template)))
