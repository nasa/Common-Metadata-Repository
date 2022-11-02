(ns cmr.dev.env.manager.components.watcher
  (:require
    [cmr.dev.env.manager.components.config :as config]
    [cmr.dev.env.manager.components.messaging :as messaging]
    [cmr.dev.env.manager.watcher.core :as watcher]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Watcher Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX TBD

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Watcher
  [builder
   watcher-subscribers
   watchers])

(defn start
  [this]
  (log/info "Starting watcher component ...")
  (messaging/batch-subscribe this (:watcher-subscribers this))
  (doseq [[service-key paths] (config/enabled-service-paths this)]
    (let [messenger (messaging/get-messenger this)
          watch (watcher/create-watcher service-key messenger)]
      ;; XXX track watches and save in component
      (log/warn "service-key:" service-key)
      (log/warn "watch:" watch)
      (log/warn "Adding paths to watch:" (vec paths))
      (watcher/add-paths watch paths)
      (log/debug "Started watcher component.")))
  this)

(defn stop
  [this]
  (log/info "Stopping watcher component ...")
  (log/debug "Stopped watcher component.")
  this)

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Watcher
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  ""
  [config-builder-fn
   subscribers]
  (map->Watcher
    {:builder config-builder-fn
     :watcher-subscribers subscribers}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX TBD
