(ns cmr.search.runner
  (:require [cmr.search.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.config :refer [check-env-vars]])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running...")
    (check-env-vars)))
