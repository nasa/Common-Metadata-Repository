(ns cmr.dev.env.manager.config
  (:require
    [clojure.string :as string]
    [cmr.dev.env.manager.util :as util]
    [cmr.transmit.config :as transmit]
    [leiningen.core.project :as project]
    [taoensso.timbre :as log]))

(def config-key :dem)

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

(defn build
  ""
  ([]
    (build false))
  ([app-key]
    (let [top-level (project/read)]
      (log/trace "top-level keys:" (keys top-level))
      (log/trace "top-level config:" top-level)
      (log/trace "dem config:" (config-key top-level))
      (when app-key
        (log/trace "app-level config:" (app-key top-level)))
      (util/deep-merge
       (default-config)
       (util/deep-merge
        {config-key (config-key top-level)}
        (when app-key
         {config-key (get-in top-level [:profiles app-key config-key])}))))))

(defn active-config
  ""
  [system config-key & args]
  (let [base-keys [:config config-key]]
    (if-not (seq args)
      (get-in system base-keys)
      (get-in system (concat base-keys args)))))

(defn app-dir
  [system]
  (active-config system config-key :app-dir))

(defn logging
  [system]
  (active-config system config-key :logging))

(defn log-level
  [system]
  (active-config system config-key :logging :level))

(defn log-nss
  [system]
  (active-config system config-key :logging :nss))

(defn enabled-services
  [system]
  (active-config system config-key :enabled-services))

(defn service-enabled?
  [system service-key]
  (contains? (enabled-services system) service-key))

(defn messaging-type
  [system]
  (active-config system config-key :messaging :type))

(defn elastic-search-opts
  [system]
  (active-config system config-key :elastic-search))

(defn elastic-search-head-opts
  [system]
  (active-config system config-key :elastic-search-head))

(defn timer-delay
  [system]
  (active-config system config-key :timer :delay))
