(ns search-relevancy-test.runner
  "Main entry point for executing tasks for the search-relevancy-test project."
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [search-relevancy-test.anomaly-fetcher :as anomaly-fetcher]
   [search-relevancy-test.boost-test :as boost-test]
   [search-relevancy-test.relevancy-test :as relevancy-test]))

(def tasks
  "List of available tasks"
  ["download-collections" "relevancy-tests" "boost-tests"])

(defn- usage
 "Prints the list of available tasks."
 [& _]
 (println (str "Available tasks: " (string/join ", " tasks))))

(defn -main
  "Runs search relevancy tasks."
  [task-name & args]
  (case task-name
        "download-collections" (anomaly-fetcher/download-and-save-all-collections)
        "relevancy-tests" (relevancy-test/relevancy-test args)
        "boost-tests" (boost-test/boost-tests-with-args args)
        usage)
  (shutdown-agents))

(comment
 (relevancy-test/relevancy-test nil)
 (boost-test/boost-tests "entry-title"))
