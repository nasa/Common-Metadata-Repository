(ns cmr.metadata-db.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.metadata-db.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.metadata-db.data.memory :as memory]
            [cmr.metadata-db.api.routes :as routes])
  (:gen-class))

(defn parse-endpoint
  "Parses an endpoint in the format host:port"
  [s]
  (let [[host port] (string/split s #":" 2)]
    {:host host :port (Integer. port)}))

(def arg-description
  [["-h" "--help" "Show help" :default false :flag true]
   ["-d" "--db" "Specifies the database type - memory or oracle" :default "memory"]
   ["-p" "--port" "The HTTP Port to listen on for requests." :default 3001 :parse-fn #(Integer. %)]])


(defn parse-args [args]
  (let [[options extra-args banner] (apply cli args arg-description)
        error-with-banner #((println "Error: " % "\n" banner) (System/exit 1))
        exit-with-banner #((println % "\n" banner) (System/exit 0))]
    (when (:help options)
      (exit-with-banner "Help:\n"))
    options))

(defn- select-db
  "return an initialization function for database based on user input parameter"
  [{:keys [db]} ]
  (case db
    "memory" (memory/create-db)
    "default" (memory/create-db)))


(defn -main
  "Starts the App."
  [& args]
  (let [{:keys [port db]} (parse-args args)
        db-params {:db db}
        db (select-db db-params)
        web-server (web/create-web-server port routes/make-api)
        log (log/create-logger)
        system (system/start (system/create-system db log web-server))]
    (info "Running...")))