(ns cmr.dev.env.manager.components.cmr.mock-echo
  (:require
    [clojure.core.async :as async]
    [clojure.java.shell :as shell]
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.util :as util]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

(defrecord MockEchoRunner [
  builder]
  component/Lifecycle

  (start [component]
    (log/info "Starting mock-echo component ...")
    (log/debug "Component keys:" (keys component))
    (let [cfg (builder :mock-echo)
          component (assoc component :config cfg)
          dir (config/app-dir component)]
      (log/trace "Built configuration:" cfg)
      (log/debug "Config:" (:config component))
      (log/debugf "Running `lein` from %s" dir)
      (let [chan (util/spawn! "lein" "mock-echo" :dir dir)]
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
