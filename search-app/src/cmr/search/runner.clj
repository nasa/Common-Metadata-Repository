(ns cmr.search.runner
  (:require [cmr.search.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.config :as cfg])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running...")
    (cfg/check-env-vars)))
