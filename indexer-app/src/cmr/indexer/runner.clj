(ns cmr.indexer.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require
   [cmr.indexer.system :as system]
   [cmr.common.log :as log :refer (info)]
   [cmr.common.config :as cfg])
  (:gen-class))

(defn -main
  "Starts the App."
  [& _args]
  (let [_system (system/start (system/create-system))]
    (info "Running...")
    (cfg/check-env-vars)))
