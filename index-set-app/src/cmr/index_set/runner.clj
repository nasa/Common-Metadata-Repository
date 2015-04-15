(ns cmr.index-set.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.index-set.system :as system]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.index-set.api.routes :as routes])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running index-set...")))
