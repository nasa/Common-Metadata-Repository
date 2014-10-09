(ns cmr.dev-system.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.dev-system.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/create-system :in-memory)
        system (system/start system)]
    (info "Running...")))