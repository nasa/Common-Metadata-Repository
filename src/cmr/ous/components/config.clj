(ns cmr.ous.components.config
  (:require
   [cmr.authz.components.config :as authz-config]
   [cmr.exchange.common.components.config :as config]
   [cmr.http.kit.components.config :as httpd-config]
   [cmr.metadata.proxy.components.config :as metadata-config]
   [cmr.ous.config :as config-lib]
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

(defn api-version
  [system]
  (:api-version (get-cfg system)))

(defn api-version-dotted
  [system]
  (str "." (api-version system)))

(defn default-content-type
  [system]
  (:default-content-type (get-cfg system)))

(defn vendor
  [system]
  (:vendor (get-cfg system)))

(defn get-cmr-search-endpoint
  [system]
  (get-search-url system))

(defn get-giovanni-endpoint
  [system]
  (config-lib/service->url (get-in (get-cfg system) [:giovanni :search])))

(defn get-edsc-endpoint
  [system]
  (config-lib/service->url (get-in (get-cfg system) [:edsc :search])))

(def authz-cache-dumpfile #'authz-config/cache-dumpfile)
(def authz-cache-init #'authz-config/cache-init)
(def authz-cache-lru-threshold #'authz-config/cache-lru-threshold)
(def authz-cache-ttl-ms #'authz-config/cache-ttl-ms)
(def authz-cache-type #'authz-config/cache-type)

(def concept-cache-dumpfile #'metadata-config/concept-cache-dumpfile)
(def concept-cache-init #'metadata-config/concept-cache-init)
(def concept-cache-ttl-ms #'metadata-config/concept-cache-ttl-ms)
(def cache-type #'metadata-config/cache-type)
(def cmr-max-pagesize #'metadata-config/cmr-max-pagesize)
(def concept-variable-version #'metadata-config/concept-variable-version)

(def get-service #'metadata-config/get-service)
(def cmr-base-url #'metadata-config/cmr-base-url)
(def opendap-base-url #'metadata-config/opendap-base-url)
(def opendap-url #'metadata-config/opendap-url)
(def get-service-url #'metadata-config/get-service-url)

;; The URLs returned by these functions have no trailing slash:
(def get-access-control-url #'authz-config/get-access-control-url)
(def get-echo-rest-url #'authz-config/get-echo-rest-url)
(def get-ingest-url #(get-service-url % :ingest))
(def get-opendap-url #(get-service-url % :opendap))
(def get-search-url #(get-service-url % :search))

;; From the HTTPD config component
(def http-entry-point-fn httpd-config/http-entry-point-fn)
(def http-assets httpd-config/http-assets)
(def http-docs httpd-config/http-docs)
(def http-port httpd-config/http-port)
(def http-index-dirs httpd-config/http-index-dirs)
(def http-replace-base-url httpd-config/http-replace-base-url)
(def http-rest-docs-base-url-template httpd-config/http-rest-docs-base-url-template)
(def http-rest-docs-outdir httpd-config/http-rest-docs-outdir)
(def http-rest-docs-source httpd-config/http-rest-docs-source)
(def http-skip-static httpd-config/http-skip-static)
(def http-base-url httpd-config/http-base-url)
(def streaming-heartbeat httpd-config/streaming-heartbeat)
(def streaming-timeout httpd-config/streaming-timeout)
(def api-routes httpd-config/api-routes)
(def site-routes httpd-config/site-routes)
(def default-page-title httpd-config/default-page-title)

;; From the common config component
(def log-color? config/log-color?)
(def log-level config/log-level)
(def log-nss config/log-nss)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config
