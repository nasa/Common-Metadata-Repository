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

(def type-name "cached-value")

(def mappings
  {type-name
   {:dynamic "strict",
    :_source {:enabled true},
    :_all {:enabled false},
    :_id   {:path "key-name"},
    :properties {:key-name string-type
                 :value string-type}}})

(def index-settings
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
        (esi/create conn index-name :settings index-settings :mappings mappings)))))

(comment

  (d/reset (:db user/system))

  (d/set-value (:db user/system) "foo" "v")
  (d/set-value (:db user/system) "bar" "v2")
  (d/set-value (:db user/system) "charlie" "v2")

  (d/get-keys (:db user/system))
  (d/get-value (:db user/system) "bar")
  (d/delete-value (:db user/system) "bar")

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
                               :size 1000
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
    (->> (esd/search conn index-name type-name
                     :query {:filtered {:query (q/match-all)
                                        :filter {:term {:key-name key-name}}}}
                     :fields [:value])
         (extract-field-from-hits :value)
         first))

  (set-value
    [this key-name value]
    (try-elastic-operation
      (esd/create conn index-name type-name
                  {:key-name key-name :value value}
                  :id key-name)))

  (delete-value
    [this key-name]
    (when-not (:found (try-elastic-operation (esd/delete conn index-name type-name key-name)))
      (errors/throw-service-error :not-found (format "No cached value with key [%s] was found"
                                                     key-name))))

  (reset
    [this]
    (when (esi/exists? conn index-name)
      (info "Deleting the cubby index")
      (esi/delete conn index-name))
    (create-index-or-update-mappings this)))

(defn create-elastic-cache-store
  [config]
  (->ElasticCacheStore config nil))