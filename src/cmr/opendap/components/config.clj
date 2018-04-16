(ns cmr.opendap.components.config
  (:require
   [cmr.opendap.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-cfg
  [system]
  (into {} (get-in system [:config :data])))

(defn get-env-cfg
  [env-var]
  (System/getenv env-var))

(defn xform
  [^Keyword type value]
  (case type
    :int (Integer/parseInt value)
    value))

(defn pull-env-var
  [cfg-data [env-var {:keys [keys type]}]]
  (if-let [env (get-env-cfg env-var)]
    (assoc-in cfg-data keys (xform type env))
    cfg-data))

(defn pull-env-vars
  [cfg-data vars-data]
  (map (partial pull-env-var cfg-data) vars-data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cache-dumpfile
  [system]
  (get-in (get-cfg system) [:caching :dumpfile]))

(defn cache-init
  [system]
  (get-in (get-cfg system) [:caching :init]))

(defn cache-lru-threshold
  [system]
  (get-in (get-cfg system) [:caching :lru :threshold]))

(defn cache-ttl-ms
  [system]
  (* (get-in (get-cfg system) [:caching :ttl :minutes]) ; minutes
     60 ; seconds
     1000 ; milliseconds
     ))

(defn cache-type
  [system]
  (get-in (get-cfg system) [:caching :type]))

(defn cmr-base-url
  [system]
  (get-in (get-cfg system) [:cmr :base-url]))

(defn http-docroot
  [system]
  (get-in (get-cfg system) [:httpd :docroot]))

(defn http-port
  [system]

  (get-in (get-cfg system) [:httpd :port]))

(defn log-level
  [system]
  (get-in (get-cfg system) [:logging :level]))

(defn log-nss
  [system]
  (get-in (get-cfg system) [:logging :nss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config [data])

(defn start
  [this]
  (log/info "Starting config component ...")
  (log/debug "Started config component.")
  (let [cfg (pull-env-vars (config/data)
                           ;; XXX Eventually, we're going to add env pull-ins
                           ;;     for other EECS vars, too ...
                           {"CMR_OPENDAP_PORT" {:keys [:httpd :port]
                                                :type :int}})]
    (log/debug "Built configuration:" (into {} cfg))
    (assoc this :data cfg)))

(defn stop
  [this]
  (log/info "Stopping config component ...")
  (log/debug "Stopped config component.")
  (assoc this :data nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Config
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Config {}))
