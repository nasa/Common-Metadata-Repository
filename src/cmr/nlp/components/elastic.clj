(ns cmr.nlp.components.elastic
  (:require
   [cmr.nlp.components.config :as config]
   [cmr.nlp.elastic.client :as client]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-conn #(get-in % [:elastic :conn]))

(defn call
  ([system ^Keyword method-key]
    (call system method-key []))
  ([system ^Keyword method-key args]
    (let [method (ns-resolve 'cmr.nlp.elastic.client
                             (symbol (name method-key)))]
      (apply method (concat [(get-conn system)] args)))))

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
