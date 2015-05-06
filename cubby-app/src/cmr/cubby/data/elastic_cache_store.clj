(ns cmr.cubby.data.elastic-cache-store
  "Defines an elasticsearch component that implements the PersistentCacheStore protocol"
  (:require [cmr.cubby.data :as d]
            [cmr.common.lifecycle :as l]
            [cmr.common.services.errors :as errors]
            [cmr.elastic-utils.connect :as esc]
            [cmr.common.log :as log :refer (debug info warn error)]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.query :as q]))

(def index-name
  "The name of the index in elastic search."
  "cubby_cached_values")

(def string-type
  "The mapping type of saved string values"
  {:type "string"
   ;; Stored so we can retrieve the value
   :store "yes"
   :index "not_analyzed"})

(def type-name
  "The name of the mapping type within the cubby elasticsearch index."
  "cached-value")

(def mappings
  "Defines the field mappings and type options for cubby stored values in elasticsearch."
  {type-name
   {:dynamic "strict",
    :_source {:enabled true},
    :_all {:enabled false},
    :_id   {:path "key-name"},
    :properties {:key-name string-type
                 :value string-type}}})

(def index-settings
  "Defines the cubby elasticsearch index settings."
  {:index
   {:number_of_shards 3,
    :number_of_replicas 1,
    :refresh_interval "1s"}})

(defn create-index-or-update-mappings
  "Creates the index needed in Elasticsearch for data storage"
  [elastic-store]
  (let [conn (:conn elastic-store)]
    (if (esi/exists? conn index-name)
      (do
        (info "Updating cubby mappings and settings")
        (doseq [[type-name mapping] mappings]
          (esi/update-mapping conn index-name type-name mapping)))
      (do
        (info "Creating cubby index")
        (esi/create conn index-name :settings index-settings :mappings mappings)
        (esc/wait-for-healthy-elastic elastic-store)))))

(comment

  (def db (get-in user/system [:apps :cubby :db]))

  (d/reset db)

  (d/set-value db "foo" "v")
  (d/set-value db "bar" "v2")
  (d/set-value db "charlie" "v2")



  (d/get-keys db)
  (d/get-value db "bar")
  (d/delete-value db "bar")

  (esd/search (-> user/system :db :conn)
              index-name type-name :query (q/match-all)
              :fields [:key-name])

  (esd/search (-> user/system :db :conn)
              index-name type-name
              :query (q/match-all)
              :size 10
              :fields [:key-name])

  (esd/search (-> user/system :db :conn)
              index-name type-name
              :query {:filtered {:query (q/match-all)
                                 :filter {:term {:key-name "foo"}}}}
              :fields [:value])

  )

(defmacro try-elastic-operation
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (errors/internal-error!
         (str "Call to Elasticsearch caught exception " (get-in (ex-data e#) [:object :body]))
         e#))))

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
   conn
   ]

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
          num-keys-in-response (count (get-in response [:hits :hits])) ]
      (when (> num-keys-found num-keys-in-response )
        (errors/internal-error!
          (format "The number of keys found, [%s], exceeded the number of keys we requested in the keys request"
                  num-keys-found)))
      (extract-field-from-hits :key-name response)))

  (get-value
    [this key-name]
    (get-in (esd/get conn index-name type-name key-name)
            [:_source :value]))

  (set-value
    [this key-name value]
    (try-elastic-operation
      (esd/create conn index-name type-name
                  {:key-name key-name :value value}
                  :id key-name
                  :refresh true)))

  (delete-value
    [this key-name]
    (:found (try-elastic-operation (esd/delete conn index-name type-name key-name :refresh true))))

  (delete-all-values
    [this]
    (try-elastic-operation
      (esd/delete-by-query conn index-name type-name (q/match-all)))
    (try-elastic-operation
      (esi/refresh conn index-name)))

  (reset
    [this]
    (when (esi/exists? conn index-name)
      (info "Deleting the cubby index")
      (esi/delete conn index-name))
    (create-index-or-update-mappings this)
    (esi/refresh conn index-name)))

(defn create-elastic-cache-store
  [config]
  (->ElasticCacheStore config nil))