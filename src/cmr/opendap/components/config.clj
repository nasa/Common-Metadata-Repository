(ns cmr.opendap.components.config
  (:require
   [cmr.authz.components.config :as authz-config]
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
  (->> [:config :data]
       (get-in system)
       (into {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn api-version
  [system]
  (:api-version (get-cfg system)))

(defn api-version-dotted
  [system]
  (str "." (api-version system)))

(defn default-content-type
  [system]
  (:default-content-type (get-cfg system)))

(def authz-cache-dumpfile #'authz-config/cache-dumpfile)
(def authz-cache-init #'authz-config/cache-init)
(def authz-cache-lru-threshold #'authz-config/cache-lru-threshold)
(def authz-cache-ttl-ms #'authz-config/cache-ttl-ms)
(def authz-cache-type #'authz-config/cache-type)

(defn concept-cache-dumpfile
  [system]
  (get-in (get-cfg system) [:concept-caching :dumpfile]))

(defn concept-cache-init
  [system]
  (get-in (get-cfg system) [:concept-caching :init]))

(defn concept-cache-ttl-ms
  [system]
  (* (get-in (get-cfg system) [:concept-caching :ttl :hours])
     60 ; minutes
     60 ; seconds
     1000 ; milliseconds
     ))

(defn cache-type
  [system]
  (get-in (get-cfg system) [:auth-caching :type]))

(defn cmr-max-pagesize
  [system]
  (get-in (get-cfg system) [:cmr :max-pagesize]))

(defn concept-variable-version
  [system]
  (get-in (get-cfg system) [:cmr :concept :variable :version]))

(defn get-service
  [system service]
  (let [svc-cfg (get-in (get-cfg system)
                        (concat [:cmr] (config/service-keys service)))]
    svc-cfg))

(defn cmr-base-url
  [system]
  (config/service->base-url (get-service system :search)))

(defn opendap-base-url
  "This function returns the cmr-opendap URL with a trailing slash, but without
  the 'opendap' appended."
  [system]
  (str (config/service->base-public-url (get-service system :opendap)) "/"))

(defn opendap-url
  "This function returns the cmr-opendap URL with a trailing slash."
  [system]
  (str (config/service->public-url (get-service system :opendap)) "/"))

(defn get-service-url
  [system service]
  (config/service->url (get-service system service)))

;; The URLs returned by these functions have no trailing slash:
(def get-access-control-url #'authz-config/get-access-control-url)
(def get-echo-rest-url #'authz-config/get-echo-rest-url)
(def get-ingest-url #(get-service-url % :ingest))
(def get-opendap-url #(get-service-url % :opendap))
(def get-search-url #(get-service-url % :search))

(defn http-assets
  [system]
  (get-in (get-cfg system) [:httpd :assets]))

(defn http-docs
  [system]
  (get-in (get-cfg system) [:httpd :docs]))

(defn http-port
  [system]
  (or (get-in (get-cfg system) [:cmr :opendap :port])
      (get-in (get-cfg system) [:httpd :port])))

(defn http-index-dirs
  [system]
  (get-in (get-cfg system) [:httpd :index-dirs]))

(defn http-replace-base-url
  [system]
  (get-in (get-cfg system) [:httpd :replace-base-url]))

(defn http-rest-docs-base-url-template
  [system]
  (get-in (get-cfg system) [:httpd :rest-docs :base-url-template]))

(defn http-rest-docs-outdir
  [system]
  (get-in (get-cfg system) [:httpd :rest-docs :outdir]))

(defn http-rest-docs-source
  [system]
  (get-in (get-cfg system) [:httpd :rest-docs :source]))

(defn http-skip-static
  [system]
  (get-in (get-cfg system) [:httpd :skip-static]))

(defn log-color?
  [system]
  (or (get-in (get-cfg system) [:cmr :opendap :logging :color])
      (get-in (get-cfg system) [:logging :color])))

(defn log-level
  [system]
  (get-in (get-cfg system) [:logging :level]))

(defn log-nss
  [system]
  (get-in (get-cfg system) [:logging :nss]))

(defn streaming-heartbeat
  [system]
  (get-in (get-cfg system) [:streaming :heartbeat]))

(defn streaming-timeout
  [system]
  (get-in (get-cfg system) [:streaming :timeout]))

(defn vendor
  [system]
  (:vendor (get-cfg system)))

(defn get-cmr-search-endpoint
  [system]
  (get-search-url system))

(defn get-giovanni-endpoint
  [system]
  (config/service->url (get-in (get-cfg system) [:giovanni :search])))

(defn get-edsc-endpoint
  [system]
  (config/service->url (get-in (get-cfg system) [:edsc :search])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config [data])

(defn start
  [this]
  (log/info "Starting config component ...")
  (log/debug "Started config component.")
  (let [cfg (config/data)]
    (log/trace "Built configuration:" cfg)
    (assoc this :data cfg)))

(defn stop
  [this]
  (log/info "Stopping config component ...")
  (log/debug "Stopped config component.")
  this)

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
