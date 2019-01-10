(ns cmr.nlp.components.elastic
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.mission-control.components.pubsub :as pubsub]
   [cmr.nlp.components.config :as config]
   [cmr.nlp.elastic.client.core :as client]
   [cmr.nlp.elastic.client.document :as document]
   [cmr.nlp.elastic.event :as event]
   [cmr.nlp.geonames :as geonames]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)
   (org.elasticsearch ElasticsearchStatusException)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-conn #(get-in % [:elastic :conn]))

(defn assoc-shape
  [geoname-data shapes-data]
  (assoc geoname-data
         :shape_coordinates
         (get shapes-data (:geonameid geoname-data))))

(defn add-document
  [json-data]
  (document/index json-data
                  geonames/index-name
                  geonames/doc-type
                  (str (:geonameid (json/parse-string json-data true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Transducers (step functions)   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ingest-transducer
  [shapes]
  (comp
    (map #(assoc-shape % shapes))
    (map json/generate-string)
    (map add-document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn system-started
  [system]
  (pubsub/publish system event/started))

(defn ingest-completed
  [system]
  (pubsub/publish system event/ingest-complete))

(defn subscribe-started
  [system func]
  (pubsub/subscribe system event/started func))

(defn subscribe-ingested
  [system func]
  (pubsub/subscribe system event/ingest-complete func))

(defn call
  ([system ^Keyword method-key]
    (call system method-key []))
  ([system ^Keyword method-key args]
    (let [conn (get-conn system)
          method (ns-resolve 'cmr.nlp.elastic.client.core
                             (symbol (name method-key)))]
      (log/trace "Got system keys:" (keys system))
      (log/trace "Got elastic:" (:elastic system))
      (log/trace "Got connection:" conn)
      (log/debug "Got method:" method)
      (log/debug "Got args:" args)
      (apply method (concat [conn] args)))))

(defn add-geonames-index
  [system]
  (try
    (call system :create-index
     ["geonames"
      (slurp
       (io/resource
        "elastic/geonames_mapping.json"))])
    (catch ElasticsearchStatusException ex
      (log/warnf "Could not add index: %s" (.getMessage ex))
      (log/trace ex))))

(defn ingest-geonames
  [system]
  (let [shapes (geonames/shapes-lookup)
        x-form (ingest-transducer shapes)
        batch-size (config/get-elastic-ingest-batch-size system)]
    (log/debug "Ingesting Geonames in batches of size" batch-size)
      (doseq [[batch-idx batch] (map-indexed
                                 vector
                                 (geonames/batch-read-gazetteer batch-size))]
        (log/debugf "Processing batch %s ..." (inc batch-idx))
        (call system :bulk [(transduce x-form conj batch)])))
  :ok)

(defn find-geonames
  [system]
  (call system :search-all [geonames/index-name]))

(defn find-geoname
  [system name]
  (call system :search [geonames/index-name "name" name]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ElasticClient [conn])

(defn start
  [this]
  (log/info "Starting Elasticsearch connection component ...")
  (let [nodes-configs (config/get-elastic-nodes this)
        _ (log/debug "Got nodes-configs:" nodes-configs)
        connection (client/create nodes-configs)]
    (log/trace "Elastic node configs:" nodes-configs)
    (log/debug "Started Elasticsearch connection component.")
    (assoc this :conn connection)))

(defn stop
  [this]
  (log/info "Stopping Elasticsearch connection component ...")
  (if-let [connection (:conn this)]
    (client/close connection))
  (log/debug "Stopped Elasticsearch connection component.")
  (assoc this :conn nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend ElasticClient
        component/Lifecycle
        lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->ElasticClient {}))
