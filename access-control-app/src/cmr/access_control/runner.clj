(ns cmr.access-control.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.access-control.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.config :as cfg])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running access-control...")
    (cfg/check-env-vars)))
