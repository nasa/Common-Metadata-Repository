(ns cmr.mock-echo.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.mock-echo.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.config :as cfg])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running mock-echo...")
    (cfg/check-env-vars)))
