(ns cmr.dev.env.manager.config
  (:require
    [clojure.string :as string]
    [cmr.dev.env.manager.util :as util]
    [cmr.transmit.config :as transmit]
    [leiningen.core.project :as project]
    [taoensso.timbre :as log]))

(def config-key :dem)

(defn read-project-clj
  []
  (project/read))

(def ^:private memoized-read-project-clj (memoize read-project-clj))

(defn default-config
  []
  {config-key {
    :elastic-search {}
    :enabled-services #{}
    :logging {
      :level :info
      :nss '[cmr]}
    :messaging {
      :type :pubsub}
    :ports {
      :access-control (transmit/access-control-port)
      :bootstrap (transmit/bootstrap-port)
      :cubby (transmit/cubby-port)
      :index-set (transmit/index-set-port)
      :indexer (transmit/indexer-port)
      :ingest (transmit/ingest-port)
      :kms (transmit/kms-port)
      :metadata-db (transmit/metadata-db-port)
      :search (transmit/search-port)
      :urs (transmit/urs-port)
      :virtual-product (transmit/virtual-product-port)}}})

(def ^:private memoized-default-config (memoize default-config))

(defn build
  ""
  ([]
    (build false))
  ([app-key]
    (let [top-level (memoized-read-project-clj)]
      (log/trace "top-level keys:" (keys top-level))
      (log/trace "top-level config:" top-level)
      (log/trace "dem config:" (config-key top-level))
      (when app-key
        (log/trace "app-level config:" (app-key top-level)))
      (util/deep-merge
       (memoized-default-config)
       (util/deep-merge
        {config-key (config-key top-level)}
        (when app-key
         {config-key (get-in top-level [:profiles app-key config-key])}))))))

(def memoized-build (memoize build))
