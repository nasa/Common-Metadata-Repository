(ns cmr.opendap.components.config
  (:require
   [cmr.authz.components.config :as authz-config]
   [cmr.exchange.common.components.config :as config]
   [cmr.http.kit.components.config :as httpd-config]
   [cmr.metadata.proxy.components.config :as metadata-config]
   [cmr.opendap.config :as config-lib]
   [cmr.ous.components.config :as ous-config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-cfg config/get-cfg)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; From the common config component
(def log-color? config/log-color?)
(def log-level config/log-level)
(def log-nss config/log-nss)

;; From the authz config component
(def authz-cache-dumpfile #'authz-config/cache-dumpfile)
(def authz-cache-init #'authz-config/cache-init)
(def authz-cache-lru-threshold #'authz-config/cache-lru-threshold)
(def authz-cache-ttl-ms #'authz-config/cache-ttl-ms)
(def authz-cache-type #'authz-config/cache-type)
(def get-access-control-url #'authz-config/get-access-control-url)
(def get-echo-rest-url #'authz-config/get-echo-rest-url)

;; From the metadata proxy config component
(def concept-cache-dumpfile #'metadata-config/concept-cache-dumpfile)
(def concept-cache-init #'metadata-config/concept-cache-init)
(def concept-cache-ttl-ms #'metadata-config/concept-cache-ttl-ms)
(def cache-type #'metadata-config/cache-type)
(def cmr-max-pagesize #'metadata-config/cmr-max-pagesize)
(def concept-variable-version #'metadata-config/concept-variable-version)
(def get-service #'metadata-config/get-service)
(def cmr-base-url #'metadata-config/cmr-base-url)
(def get-service-url #'metadata-config/get-service-url)
(def get-cmr-search-endpoint metadata-config/get-search-url)
(def get-ingest-url metadata-config/get-ingest-url)
(def get-search-url metadata-config/get-search-url)

;; From the HTTPD config component
(def http-entry-point-fn httpd-config/http-entry-point-fn)
(def http-assets httpd-config/http-assets)
(def http-docs httpd-config/http-docs)
(def http-index-dirs httpd-config/http-index-dirs)
(def http-replace-base-url httpd-config/http-replace-base-url)
(def http-rest-docs-base-url-template httpd-config/http-rest-docs-base-url-template)
(def http-rest-docs-outdir httpd-config/http-rest-docs-outdir)
(def http-rest-docs-source httpd-config/http-rest-docs-source)
(def http-skip-static httpd-config/http-skip-static)
(def streaming-heartbeat httpd-config/streaming-heartbeat)
(def streaming-timeout httpd-config/streaming-timeout)
(def api-routes httpd-config/api-routes)
(def site-routes httpd-config/site-routes)
(def default-page-title httpd-config/default-page-title)

;; From the OUS plugin config component
(def opendap-base-url ous-config/opendap-base-url)
(def opendap-url ous-config/opendap-url)
(def get-edsc-endpoint ous-config/get-edsc-endpoint)
(def get-giovanni-endpoint ous-config/get-giovanni-endpoint)
(def get-opendap-url ous-config/get-opendap-url)

;; Overrides of the HTTPD config component
(defn http-port
  [system]
  (or (get-in (get-cfg system) [:cmr :opendap :port])
      (httpd-config/http-port system)))

(defn http-base-url
  [system]
  (or (get-in (get-cfg system) [:cmr :opendap :relative :root :url])
      (httpd-config/http-base-url system)))

(defn vendor
  [system]
  (:vendor (get-cfg system)))

(defn api-version
  [system]
  (:api-version (get-cfg system)))

(defn api-version-dotted
  [system]
  (str "." (api-version system)))

(defn default-content-type
  [system]
  (:default-content-type (get-cfg system)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config
