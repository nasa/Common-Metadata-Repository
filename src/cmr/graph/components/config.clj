(ns cmr.graph.components.config
  (:require
   [cmr.exchange.common.components.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def get-cfg config/get-cfg)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn elastic-host
  [system]
  (get-in (get-cfg system) [:elastic :host]))

(defn elastic-port
  [system]
  (get-in (get-cfg system) [:elastic :port]))

(defn elastic-timeout
  [system]
  (get-in (get-cfg system) [:elastic :timeout]))

(defn http-port
  [system]
  (get-in (get-cfg system) [:httpd :port]))

(defn http-docroot
  [system]
  (get-in (get-cfg system) [:httpd :docroot]))

(def log-color? config/log-color?)
(def log-level config/log-level)
(def log-nss config/log-nss)

(defn neo4j-host
  [system]
  (get-in (get-cfg system) [:neo4j :host]))

(defn neo4j-port
  [system]
  (get-in (get-cfg system) [:neo4j :port]))

(defn neo4j-db-path
  [system]
  (get-in (get-cfg system) [:neo4j :db-path]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config
