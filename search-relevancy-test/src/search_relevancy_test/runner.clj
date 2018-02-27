(ns search-relevancy-test.runner
  "Main entry point for executing tasks for the search-relevancy-test project."
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [search-relevancy-test.anomaly-analyzer :as anomaly-analyzer]
   [search-relevancy-test.anomaly-fetcher :as anomaly-fetcher]
   [search-relevancy-test.boost-test :as boost-test]
   [search-relevancy-test.core :as core]
   [search-relevancy-test.relevancy-test :as relevancy-test]
   [search-relevancy-test.top-n :as top-n]))

(def tasks
  "List of available tasks"
  ["download-collections" "relevancy-tests" "boost-tests" "analyze-test"])

(defn- usage
 "Prints the list of available tasks."
 [& _]
 (println (str "Available tasks: " (string/join ", " tasks))))

(defn -main
  "Runs search relevancy tasks."
  [task-name & args]
  (case task-name
        "analyze-test" (if (some? (second args))
                         (anomaly-analyzer/analyze-test (first args) (second args))
                         (anomaly-analyzer/analyze-test (first args)))
        "download-collections" (anomaly-fetcher/download-and-save-all-collections)
        "relevancy-tests" (relevancy-test/relevancy-test args)
        "edsc-relevancy-tests" (relevancy-test/edsc-relevancy-test args)
        "boost-tests" (boost-test/boost-tests-with-args args)
        "top-n-tests" (top-n/run-top-n-tests core/top-n-anomaly-filename)
        usage)
  (shutdown-agents))

(comment
 (relevancy-test/relevancy-test nil)
 (boost-test/boost-tests "entry-title"))
