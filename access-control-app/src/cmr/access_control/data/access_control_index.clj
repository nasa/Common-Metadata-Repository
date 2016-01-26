(ns cmr.access-control.data.access-control-index
  "Performs search and indexing of access control data."
  (:require [cmr.elastic-utils.mapping :as m :refer [defmapping]]
            [cmr.elastic-utils.connect :as esc]
            [cmr.elastic-utils.config :as es-config]
            [cmr.common.lifecycle :as l]))

(def group-index-name
  "The name of the index in elastic search."
  "groups")

(def group-type-name
  "The name of the mapping type within the cubby elasticsearch index."
  "access-group")

(defmapping group-mappings group-type-name
  "Defines the field mappings and type options for indexing groups in elasticsearch."
  {:name (m/stored m/string-field-mapping)
   :provider-id (m/stored m/string-field-mapping)
   :description (m/not-indexed (m/stored m/string-field-mapping))
   :legacy-guid (m/stored m/string-field-mapping)
   :members m/string-field-mapping
   :member-count (m/stored (m/not-indexed m/int-field-mapping))})

(def group-index-settings
  "Defines the elasticsearch index settings."
  {:number_of_shards 3,
   :number_of_replicas 1,
   :refresh_interval "1s"})


(defn create-index-or-update-mappings
  "Creates the index needed in Elasticsearch for data storage"
  [elastic-store]
  (m/create-index-or-update-mappings
    group-index-name group-index-settings group-type-name group-mappings elastic-store))

(defrecord ElasticStore
  [;; configuration of host and port for elasticsearch
   config

   ;; The connection to elasticsearch
   conn]


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  l/Lifecycle

  (start
    [this system]
    (assoc this :conn (esc/try-connect config)))

  (stop [this system]
        this))


(defn create-elastic-store
  []
  (->ElasticStore (es-config/elastic-config) nil))