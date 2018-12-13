(ns cmr.nlp.elastic.ingest
  "This is a high-level API for performing cmr-nlp-specific ingest: creating
  all Elasticsearch indices as required by the application, and then ingesting
  whatever data is needed by the application, for example, in support of
  Geonames.

  For regular Elasticsearch ingest, see the approporiate methods defined in
  `cmr.nlp.elastic.client.core`."
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as logger]
   [cmr.nlp.components.core :as components]
   [cmr.nlp.components.elastic :as elastic-component]
   [cmr.nlp.elastic.client.core :as client]
   [cmr.nlp.geonames :as geonames]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]
   [trifl.java :as java])
  (:gen-class))

(defn -main
  [& args]
  (logger/set-level! ['cmr] :info)
  (let [system (components/init)]
    (component/start system)
    (log/info "Running cmr-nlp ingest ...")
    ;; Create index/indices
    (elastic-component/add-geonames-index system)
    ;; Ingest data
    (component/stop system))
  (System/exit 0))
