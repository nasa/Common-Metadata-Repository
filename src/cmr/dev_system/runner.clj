(ns cmr.dev-system.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.dev-system.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.dev-system.control :as control])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/create-system :in-memory)
        control-server (web/create-web-server 2999 control/make-api)
        system (assoc-in system [:components :control-server] control-server)
        system (system/start system)]
    (info "Running...")))