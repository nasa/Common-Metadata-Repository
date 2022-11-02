(ns cmr.nlp.elastic.ingest
  "This namespace is intended to be used from a CLI for performing high-level,
  cmr-nlp-specific ingest:
  * creating all Elasticsearch indices as required by the application, and
  * ingesting whatever data is needed by the application, for example, in
    support of Geonames.

  For regular Elasticsearch ingest, see the approporiate methods defined in
  `cmr.nlp.elastic.client.core`."
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as logger]
   [cmr.nlp.components.core :as components]
   [cmr.nlp.components.elastic :as elastic-component]
   [cmr.nlp.elastic.client.core :as client]
   [cmr.nlp.elastic.event :as event]
   [cmr.nlp.geonames :as geonames]
   [cmr.nlp.util :as util]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]
   [trifl.java :as java])
  (:gen-class))

(defn not-ready?
  [system _throwable]
  (nil? (elastic-component/get-conn system)))


(defn run
  [system _event-data]
  (log/info "Running cmr-nlp ingest ...")
  ;; Create index/indices
  (elastic-component/add-geonames-index system)
  ;; Ingest data
  (elastic-component/ingest-geonames system)
  ;; Notify mission-control that we're done
  (elastic-component/ingest-completed system))

(defn shutdown
  ([system]
   (shutdown system {}))
  ([system _event-data]
   (component/stop system)
   (log/info "cmr-nlp ingest complete.")
   (log/info "Exiting ...")
   (System/exit 0)))

(defn elastic-timeout
  [system]
  (log/error "System was never ready (Elasticsearch connection remained nil).")
  (shutdown system))

(defn -main
  [& args]
  (logger/set-level! ['cmr] :debug)
  (let [system (component/start (components/init))]
    (log/trace "System keys:" (keys system))
    (log/trace "pubsub keys:" (keys (:pubsub system)))
    (elastic-component/subscribe-started system run)
    (elastic-component/subscribe-ingested system shutdown)
    (util/exponential-backoff 1000
                              2
                              10000
                              #(not-ready? system %)
                              #(elastic-component/system-started system)
                              #(elastic-timeout system))))
