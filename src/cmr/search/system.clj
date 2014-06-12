(ns cmr.search.system
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.common.cache :as cache]
            [cmr.search.api.routes :as routes]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.system-trace.context :as context]
            [cmr.common.config :as cfg]
            [cmr.transmit.config :as transmit-config]
            [cmr.elastic-utils.config :as es-config]))

;; Design based on http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts

(def search-public-protocol (cfg/config-value :search-public-protocol "http"))
(def search-public-host (cfg/config-value :search-public-host "localhost"))
(def search-public-port (cfg/config-value :search-public-port 3003 transmit-config/parse-port))
(def search-relative-root-url (cfg/config-value :search-relative-root-url ""))

(def search-public-conf
  {:protocol search-public-protocol
   :host search-public-host
   :port search-public-port
   :relative-root-url search-relative-root-url})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :search-index :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :search-index (idx/create-elastic-search-index (es-config/elastic-config))
             :web (web/create-web-server (transmit-config/search-port) routes/make-api)
             :cache (cache/create-cache)
             :zipkin (context/zipkin-config "Search" false)
             :search-public-conf search-public-conf}]
    (transmit-config/system-with-connections sys [:metadata-db :index-set])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/start % system)))
                               this
                               component-order)]
    (info "System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/stop % system)))
                               this
                               (reverse component-order))]
    (info "System stopped")
    stopped-system))
