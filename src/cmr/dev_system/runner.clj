(ns cmr.dev-system.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.dev-system.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.dev-system.control :as control])
  (:gen-class))


(def arg-description
  [["-h" "--help" "Show help" :default false :flag true]])

(defn parse-args [args]
  (let [[options extra-args banner] (apply cli args arg-description)
        error-with-banner #((println "Error: " % "\n" banner) (System/exit 1))
        exit-with-banner #((println % "\n" banner) (System/exit 0))]
    (when (:help options)
      (exit-with-banner "Help:\n"))
    options))

(defn -main
  "Starts the App."
  [& args]
  (let [{:keys [port]} (parse-args args)
        system (system/create-system :in-memory)
        control-server (web/create-web-server 2999 control/make-api)
        system (assoc-in system [:components :control-server] control-server)
        system (system/start system)]
    (info "Running...")))