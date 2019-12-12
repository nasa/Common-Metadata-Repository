(ns cmr.dev.env.manager.config
  (:require
    [clojure.string :as string]
    [cmr.dev.env.manager.util :as util]
    [cmr.transmit.config :as transmit]
    [leiningen.core.project :as project]
    [taoensso.timbre :as log]))

(def internal-cfg-key :dem)
(def external-cfg-key :cmr)

(defn read-project-clj
  []
  (project/read))

(def memoized-read-project-clj (memoize read-project-clj))

(defn default-config
  []
  {internal-cfg-key {
    :elastic-search {}
    :enabled-services #{}
    :logging {
      :level :info
      :nss '[cmr]}
    :messaging {
      :type :pubsub}}})

(def memoized-default-config (memoize default-config))

(defn default-cmr-config
  []
  {external-cfg-key {
    :ports {
      :access-control (transmit/access-control-port)
      :bootstrap (transmit/bootstrap-port)
      :indexer (transmit/indexer-port)
      :ingest (transmit/ingest-port)
      :kms (transmit/kms-port)
      :metadata-db (transmit/metadata-db-port)
      :search (transmit/search-port)
      :urs (transmit/urs-port)
      :virtual-product (transmit/virtual-product-port)}}})

(def memoized-default-cmr-config (memoize default-cmr-config))

(defn profiles-config
  []
  (:profiles (memoized-read-project-clj)))

(def memoized-profiles-config (memoize profiles-config))

(defn cmr-config
  []
  (util/deep-merge
    (memoized-default-cmr-config)
    {external-cfg-key
      (dissoc (memoized-profiles-config)
              :dev :test :docs :ubercompile
              :instrumented :lint :custom-repl)}))

(def memoized-cmr-config (memoize cmr-config))

(defn service-config
  [service-key]
  (get (memoized-profiles-config) service-key))

(def memoized-service-config (memoize service-config))

(defn build
  ""
  ([]
    (build false))
  ([service-key]
    (let [top-level (memoized-read-project-clj)
          service-level (memoized-service-config service-key)]
      (log/trace "top-level keys:" (keys top-level))
      (log/trace "top-level config:" top-level)
      (log/trace "dem config:" (internal-cfg-key top-level))
      (when service-key
        (log/trace "service-level config:" service-level))
      (merge
        (util/deep-merge
          (memoized-default-config)
          {internal-cfg-key (internal-cfg-key top-level)})
        (when service-key
          {service-key service-level})
        (memoized-cmr-config)))))

(def memoized-build (memoize build))
