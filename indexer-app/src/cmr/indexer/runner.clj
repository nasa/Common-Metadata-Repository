(ns cmr.indexer.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.indexer.system :as system]
            [clojure.string :as string]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.indexer.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.common.config :as cfg])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running...")
    (cfg/check-env-vars)))
