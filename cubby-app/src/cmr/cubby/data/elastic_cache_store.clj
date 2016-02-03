(ns cmr.cubby.data.elastic-cache-store
  "Defines an elasticsearch component that implements the PersistentCacheStore protocol"
  (:require [cmr.cubby.data :as d]
            [cmr.common.lifecycle :as l]
            [cmr.common.services.errors :as errors]
            [cmr.elastic-utils.connect :as esc]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.elastic-utils.index-util :as m :refer [defmapping]]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]))

(def index-name
  "The name of the index in elastic search."
  "cubby_cached_values")

(def not-indexed-string-type
  "The mapping type of saved string values"
  {:type "string"
   ;; Stored so we can retrieve the value
   :store "yes"
   ;; Do not index the field, otherwise strings are limited to 32KB. The theoretical max size for
   ;; a field that is not indexed is 2GB.
   :index "no"})

(def indexed-string-type
  "A string that is indexed but not analyzed."
  {:type "string"
   ;; Stored so we can retrieve the value
   :store "yes"
   :index "not_analyzed"})

(def type-name
  "The name of the mapping type within the cubby elasticsearch index."
  "cached-value")

(defmapping mappings type-name
  "Defines the field mappings and type options for cubby stored values in elasticsearch."
  {:_id {:path "key-name"}
   :_source {:enabled true}}
  {:key-name (m/stored m/string-field-mapping)

   ;; Deprecated, remove in future sprint. We changed the value to support values
   ;; larger than 32KB.
   :value (m/stored m/string-field-mapping)

   :value2 (m/not-indexed (m/stored m/string-field-mapping))})

(def index-settings
  "Defines the cubby elasticsearch index settings."
  {:number_of_shards 3,
   :number_of_replicas 1,
   :refresh_interval "1s"})

(defn create-index-or-update-mappings
  "Creates the index needed in Elasticsearch for data storage"
  [elastic-store]
  (m/create-index-or-update-mappings
    index-name index-settings type-name mappings elastic-store))

(defn- extract-field-from-hits
  "Extracts the given field from the hits returned. Expects that there is only 1 value indexed per
  document."
  [field response]
  (for [{{[value] field} :fields} (get-in response [:hits :hits])]
    value))

(defrecord ElasticCacheStore
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
        this)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  d/PersistentCacheStore

  (get-keys
    [this]
    (let [response (esd/search conn index-name type-name
                               :query (q/match-all)
                               ;; The size here must be specified to get more than 10 keys back.
                               ;; We are limited to a maximum number of keys that is specified here.
                               :size 10000
                               :fields [:key-name])
          num-keys-found (get-in response [:hits :total])
          num-keys-in-response (count (get-in response [:hits :hits]))]
      (when (> num-keys-found num-keys-in-response)
        (errors/internal-error!
          (format "The number of keys found, [%s], exceeded the number of keys we requested in the keys request"
                  num-keys-found)))
      (extract-field-from-hits :key-name response)))

  (get-value
    [this key-name]
    (get-in (esd/get conn index-name type-name key-name)
            [:_source :value2]))

  (set-value
    [this key-name value]
    (m/try-elastic-operation
      (esd/create conn index-name type-name
                  {:key-name key-name :value2 value}
                  :id key-name
                  :refresh true)))

  (delete-value
    [this key-name]
    (:found (m/try-elastic-operation (esd/delete conn index-name type-name key-name :refresh true))))

  (delete-all-values
    [this]
    (m/try-elastic-operation
      (esd/delete-by-query conn index-name type-name (q/match-all)))
    (m/try-elastic-operation
      (esi/refresh conn index-name)))

  (reset
    [this]
    (when (esi/exists? conn index-name)
      (info "Deleting the cubby index")
      (esi/delete conn index-name))
    (create-index-or-update-mappings this)))

(defn create-elastic-cache-store
  [config]
  (->ElasticCacheStore config nil))