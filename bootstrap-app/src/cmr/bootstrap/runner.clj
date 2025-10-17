(ns cmr.bootstrap.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require
   [cmr.bootstrap.system :as system]
   [cmr.common.config :as cfg]
   [cmr.common.log :refer (info)])
  (:gen-class))

(defn -main
  "Starts the App."
  [& _args]
  (let [_system (system/start (system/create-system))]
    (info "Running bootstrap...")
    (cfg/check-env-vars)))
