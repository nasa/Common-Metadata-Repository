(ns cmr.metadata-db.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.metadata-db.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.oracle :as mo]
            [cmr.metadata-db.api.routes :as routes])
  (:gen-class))

(def arg-description
  [["-h" "--help" "Show help" :default false :flag true]
   ["-p" "--port" "The HTTP Port to listen on for requests." :default 3001 :parse-fn #(Integer. %)]])

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
  (let [{:keys [port db]} (parse-args args)
        db (oracle/create-db (oracle/db-spec))
        web-server (web/create-web-server port routes/make-api)
        system (assoc (system/create-system) :db db :web web-server)
        system (system/start system)]
    (info "Running...")))