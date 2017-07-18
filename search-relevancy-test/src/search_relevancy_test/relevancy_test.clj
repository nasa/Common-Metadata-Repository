(ns search-relevancy-test.relevancy-test
  "Functions to run the main relevancy tests"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [search-relevancy-test.core :as core]
   [search-relevancy-test.ingest :as ingest]
   [search-relevancy-test.reporter :as reporter]
   [search-relevancy-test.result-logger :as result-logger]
   [clojure.set :as set]))

(def base-search-path
  "http://localhost:3003/collections")

(defn- perform-search
  "Perform the search from the anomaly test by appending the search to the end
  of the base search path. Return results in JSON and parse."
  [anomaly-test search-params]
  (let [response (client/get
                  (str base-search-path (:search anomaly-test) search-params)
                  {:headers {"Accept" "application/json"}})]
    (json/parse-string (:body response) true)))

(defn- perform-search-test
  "Perform the anomaly test. Perform the search and compare the order of the
  results to the order specified in the test. Print messages to the REPL."
  [anomaly-test search-params]
  (let [search-results (perform-search anomaly-test search-params)
        test-concept-ids (string/split (:concept-ids anomaly-test) #",")
        all-result-ids (map :id (:entry (:feed search-results)))
        result-ids (filter #(contains? (set test-concept-ids) %) all-result-ids)]
    (reporter/analyze-search-results anomaly-test test-concept-ids result-ids)))

(defn string-key-to-int-sort
  "Sorts by comparing as integers."
  [v1 v2]
  (< (Integer/parseInt (key v1))
     (Integer/parseInt (key v2))))

(defn- perform-tests
  "Read the anomaly test CSV and perform each test"
  [search-params]
  (for [tests-by-anomaly (sort string-key-to-int-sort
                                 (group-by :anomaly (core/read-anomaly-test-csv)))
          :let [test-count (count (val tests-by-anomaly))]]
    (do
      (println (format "Anomaly %s %s"
                       (key tests-by-anomaly)
                       (if (> test-count 1) (format "(%s tests)" test-count) "")))
      (doall
        (for [individual-test (val tests-by-anomaly)]
          (perform-search-test individual-test search-params))))))

(defn run-anomaly-tests
  "Run all of the anomaly tests from the CSV file"
  [args search-params]
  (println "Running tests")
  (let [test-results (flatten (doall (perform-tests search-params)))]
    (reporter/print-result-summary test-results)
    (println "Logging test results")
    (result-logger/log-test-run test-results args)
    test-results))

(defn test-setup
  "Reset the system, ingest community usage metrics and ingest all of the test data"
  []
  (let [test-files (core/test-files)]
    (println "Creating providers")
    (ingest/create-providers test-files)
    (println "Ingesting community usage metrics and test collections")
    (ingest/ingest-community-usage-metrics) ;; Needs to happen before ingest
    (ingest/ingest-test-files test-files)))

(defn relevancy-test
  "Reset the system, ingest all of the test data, and perform the searches from
  the anomaly testing CSV. Can specify additional search params to append to the
  search via search-params"
  ([args]
   (relevancy-test args nil))
  ([args search-params]
   (test-setup)
   (run-anomaly-tests args search-params)))
