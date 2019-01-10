(ns cmr.plugin.jar.components.config
  (:require
   [cmr.exchange.common.components.config :as config]
   [cmr.exchange.common.util :as util]
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

(def log-color? config/log-color?)
(def log-level config/log-level)
(def log-nss config/log-nss)

(defn default-plugin-name
  [system]
  (get-in (get-cfg system) [:plugin :registry :default :plugin-name]))

(defn default-plugin-type
  [system]
  (get-in (get-cfg system) [:plugin :registry :default :plugin-type]))

(defn default-config-file
  [system]
  (get-in (get-cfg system) [:plugin :registry :default :config-file]))

(defn default-route-keys
  [system]
  (get-in (get-cfg system) [:plugin :registry :web :route-keys]))

(defn api-route-key
  [system]
  (get-in (get-cfg system) [:plugin :registry :web :api-route-key]))

(defn site-route-key
  [system]
  (get-in (get-cfg system) [:plugin :registry :web :site-route-key]))

(defn jarfiles-reducer
  [system]
  (-> (get-cfg system)
      (get-in [:plugin :jarfiles :reducer-factory])
      util/resolve-fully-qualified-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Implemented in cmr.exchange.common.components.config
