(ns cmr.virtual-product.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.virtual-product.system :as system]
            [cmr.common.log :refer (info)]
            [cmr.common.config :as cfg])
  (:gen-class))

(defn -main
  "Starts the App."
  [& _args]
  (let [_system (system/start (system/create-system))]
    (info "Running virtual-product...")
    (cfg/check-env-vars)))
