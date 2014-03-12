(ns cmr.search.runner
  (:require [cmr.search.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [taoensso.timbre :refer (debug info warn error)])
  (:gen-class))

(defn parse-endpoint
  "Parses an endpoint in the format host:port"
  [s]
  (let [[host port] (string/split s #":" 2)]
    {:host host :port (Integer. port)}))

(def arg-description
  [["-h" "--help" "Show help" :default false :flag true]
   ["-p" "--port" "The HTTP Port to listen on for requests." :default 4242 :parse-fn #(Integer. %)]])


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
        sys-config (system/map->Config {:port port})
        system (system/start (system/create-system sys-config))]
    (info "Running...")))
