(ns cmr.indexer.runner
  "Entry point for the application. Defines a main method that accepts arguments."
  (:require [cmr.indexer.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.string :as string]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.indexer.api.routes :as routes]
            [cmr.common.api.web-server :as web])
  (:gen-class))

(defn parse-endpoint
  "Parses an endpoint in the format host:port"
  [s]
  (let [[host port] (string/split s #":" 2)]
    {:host host :port (Integer. port)}))

(def arg-description
  [["-h" "--help" "Show help" :default false :flag true]
   ["-p" "--port" "The HTTP Port to listen on for requests." :default 3004 :parse-fn #(Integer. %)]])


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
        web-server (web/create-web-server port routes/make-api)
        system (assoc (system/create-system) :web web-server)
        system (system/start system)]
    (info "Running...")))
