(ns cmr.dev.env.manager.components.config
  (:require
    [cmr.dev.env.manager.config :as config]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

(defrecord Config [
  builder]
  component/Lifecycle

  (start [component]
    (log/info "Starting config component ...")
    (log/debug "Started config component.")
    (let [cfg (builder)]
      (log/trace "Built configuration:" cfg)
      (merge component cfg)))

  (stop [component]
    (log/info "Stopping config component ...")
    (log/debug "Stopped config component.")
    (assoc component :dem nil)))

(defn create-component
  ""
  [config-builder-fn]
  (map->Config
    {:builder config-builder-fn}))
