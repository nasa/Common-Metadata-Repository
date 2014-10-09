(ns cmr.metadata-db.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.metadata-db.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.api.routes :as routes])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running...")))