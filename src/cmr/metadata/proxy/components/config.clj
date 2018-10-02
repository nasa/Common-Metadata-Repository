(ns cmr.metadata.proxy.components.config
  (:require
   [cmr.authz.components.config :as authz-config]
   [cmr.exchange.common.components.config :as config]
   [cmr.http.kit.components.config :as httpd-config]
   [cmr.metadata.proxy.config :as config-lib]
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

(defn default-content-type
  [system]
  (:default-content-type (get-cfg system)))

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
                        (concat [:cmr] (config-lib/service-keys service)))]
    svc-cfg))

(defn cmr-base-url
  [system]
  (config-lib/service->base-url (get-service system :search)))

(defn get-service-url
  [system service]
  (config-lib/service->url (get-service system service)))

;; The URLs returned by these functions have no trailing slash:
(def get-access-control-url #'authz-config/get-access-control-url)
(def get-echo-rest-url #'authz-config/get-echo-rest-url)
(def get-ingest-url #(get-service-url % :ingest))
(def get-search-url #(get-service-url % :search))

;; From the common config component
(def log-color? config/log-color?)
(def log-level config/log-level)
(def log-nss config/log-nss)

(defn vendor
  [system]
  (:vendor (get-cfg system)))

(defn get-cmr-search-endpoint
  [system]
  (get-search-url system))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config
