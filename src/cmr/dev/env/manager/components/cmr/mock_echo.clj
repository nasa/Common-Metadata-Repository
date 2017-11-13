(ns cmr.dev.env.manager.components.cmr.mock-echo
  (:require
    [clojure.core.async :as async]
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.process :as process]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;; XXX Maybe generalize this for use by all apps?
(defrecord MockEchoRunner [
  builder]
  component/Lifecycle

  (start [component]
    (log/info "Starting mock-echo component ...")
    (log/debug "Component keys:" (keys component))
    (let [cfg (builder :mock-echo)
          component (assoc component :config cfg)]
      (log/trace "Built configuration:" cfg)
      (log/debug "Config:" (:config component))
      (let [chan (process/spawn! "lein" "mock-echo")]
        (log/debug "Started mock-echo component.")
        (assoc component :channel chan))))

  (stop [component]
    (log/info "Stopping mock-echo component ...")
    (log/debug "Stopped mock-echo component.")
    (async/close! (:channel component))
    component))

(defn create-mock-echo-component
  ""
  [config-builder-fn]
  (map->MockEchoRunner
    {:builder config-builder-fn}))
