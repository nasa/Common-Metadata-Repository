(ns cmr.dev-system.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require
   [clojure.string :as string]
   [cmr.common.config :as config]
   [cmr.common.log :refer [info]]
   [cmr.dev-system.system :as system])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (config/print-all-configs-docs)
  (let [system (system/start (system/create-system))]
    (.addShutdownHook (Runtime/getRuntime)
                      (new Thread #(system/stop system)))
    (info "Running...")
    (config/check-env-vars)))
