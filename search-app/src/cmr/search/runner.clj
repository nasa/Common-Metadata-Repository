(ns cmr.search.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.common-app.app-events :as events]
            [cmr.common.config :as cfg]
            [cmr.search.system :as system]))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (cfg/check-env-vars)
    (events/stop-on-exit-hook (:instance-name system) #(system/stop system))
    (events/dump-on-exit-hook (:instance-name system))))
