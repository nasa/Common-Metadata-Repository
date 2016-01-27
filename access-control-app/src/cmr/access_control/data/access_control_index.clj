(ns cmr.access-control.data.access-control-index
  "Performs search and indexing of access control data."
  (:require [cmr.elastic-utils.index-util :as m :refer [defmapping]]
            [cmr.elastic-utils.connect :as esc]
            [cmr.access-control.data :as d]
            [cmr.elastic-utils.config :as es-config]
            [cmr.common.lifecycle :as l]
            [clojure.edn :as edn]))

(def ^:private group-index-name
  "The name of the index in elastic search."
  "groups")

(def ^:private group-type-name
  "The name of the mapping type within the cubby elasticsearch index."
  "access-group")

(defmapping ^:private group-mappings group-type-name
  "Defines the field mappings and type options for indexing groups in elasticsearch."
  {:name (m/stored m/string-field-mapping)
   :provider-id (m/stored m/string-field-mapping)
   :description (m/not-indexed (m/stored m/string-field-mapping))
   :legacy-guid (m/stored m/string-field-mapping)
   :members m/string-field-mapping
   ;; Member count is returned in the group response. The list of members is returned separately so
   ;; we don't store the members in the elastic index. If members end up being stored at some point
   ;; we can get rid of this field.
   :member-count (m/stored (m/not-indexed m/int-field-mapping))})

(def ^:private group-index-settings
  "Defines the elasticsearch index settings."
  {:number_of_shards 3,
   :number_of_replicas 1,
   :refresh_interval "1s"})

(defn create-index-or-update-mappings
  "Creates the index needed in Elasticsearch for data storage"
  [elastic-store]
  (m/create-index-or-update-mappings
    group-index-name group-index-settings group-type-name group-mappings elastic-store))

(defmulti index-concept
  "Indexes the concept map in elastic search."
  (fn [context concept-map]
    (:concept-type concept-map)))

(defmethod index-concept :access-group
  [context concept-map]
  (let [group (edn/read-string (:metadata concept-map))
        elastic-doc (assoc group :member-count (count (:members group [])))
        {:keys [concept-id revision-id]} concept-map
        elastic-store (get-in context [:system :index])]
    (m/save-elastic-doc
     elastic-store group-index-name group-type-name concept-id elastic-doc revision-id
     {:ignore-conflict? true})))


;; TODO write test that deleting a provider deletes all the groups in that provider
;; -- or make this a new issue



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