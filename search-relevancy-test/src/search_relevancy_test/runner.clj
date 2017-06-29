(ns search-relevancy-test.runner
  "Main entry point for executing tasks for the search-relevancy-test project."
  (:gen-class)
  (:require
   [clojure.string :as string]
   [search-relevancy-test.anomaly-fetcher :as anomaly-fetcher]
   [search-relevancy-test.core :as core]))

(def tasks
  "List of available tasks"
  ["download-collections" "relevancy-tests"])

(defn- usage
 "Prints the list of available tasks."
 [& _]
 (println (str "Available tasks: " (string/join ", " tasks))))

(defn -main
  "Runs search relevancy tasks."
  [task-name & args]
  (case task-name
        "download-collections" (anomaly-fetcher/download-and-save-all-collections)
        "relevancy-tests" (core/relevancy-test)
        usage)
  (shutdown-agents))
