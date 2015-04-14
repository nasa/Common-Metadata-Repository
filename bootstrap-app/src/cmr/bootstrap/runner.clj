(ns cmr.bootstrap.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.bootstrap.system :as system]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.bootstrap.api.routes :as routes])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running bootstrap...")))
