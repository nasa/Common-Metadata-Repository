(ns cmr.cubby.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.cubby.system :as system]
            [cmr.common.log :refer (debug info warn error)])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (system/start (system/create-system))
  (info "Running cubby..."))
