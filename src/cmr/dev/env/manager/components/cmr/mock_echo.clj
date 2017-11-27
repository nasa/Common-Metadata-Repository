(ns cmr.dev.env.manager.components.cmr.mock-echo
  (:require
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
      (let [process-data (process/spawn! "lein" "mock-echo")]
        (log/debug )
        (log/debug "Started mock-echo component.")
        (assoc component :process-data process-data))))

  (stop [component]
    (log/info "Stopping mock-echo component ...")
    (println "componentn keys:" (keys component))
    (println "process-data:" (:process-data component))
    (process/terminate! (:process-data component))
    (log/debug "Stopped mock-echo component.")
    component))

(defn create-mock-echo-component
  ""
  [config-builder-fn]
  (map->MockEchoRunner
    {:builder config-builder-fn
     :process-data nil}))
