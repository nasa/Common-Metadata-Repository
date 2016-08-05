(ns cmr.virtual-product.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.virtual-product.system :as system]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.config :refer [check-env-vars]])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running virtual-product...")
    (check-env-vars)))
