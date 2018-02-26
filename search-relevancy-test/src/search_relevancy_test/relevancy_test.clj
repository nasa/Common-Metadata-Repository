(ns search-relevancy-test.relevancy-test
  "Functions to run the main relevancy tests"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [search-relevancy-test.core :as core]
   [search-relevancy-test.ingest :as ingest]
   [search-relevancy-test.edsc-log-parser :as edsc-log-parser]
   [search-relevancy-test.reporter :as reporter]
   [search-relevancy-test.result-logger :as result-logger]
   [clojure.set :as set]))

(def page-size
  100)

(def base-search-path
  "http://localhost:3003/collections")

(defn- add-concept-ids-to-search
  "Takes a search string and adds concept-ids to the search."
  [query-string concept-ids]
  (let [concept-ids (if (string? concept-ids)
                      (string/split concept-ids #",")
                      concept-ids)
        concept-ids-query-string (string/join "&concept_id[]=" concept-ids)]
    (format "%s&concept_id[]=%s" query-string concept-ids-query-string)))

(defn perform-search
  "Perform the search from the anomaly test by appending the search to the end
  of the base search path. Return results in JSON and parse."
  [anomaly-test search-params]
  (let [response (client/get
                  (str base-search-path
                       (add-concept-ids-to-search (:search anomaly-test) (:concept-ids anomaly-test))
                       search-params
                       (format "&page_size=%d" page-size))
                  {:headers {"Accept" "application/json"}})]
    (json/parse-string (:body response) true)))

(defn- perform-search-test
  "Perform the anomaly test. Perform the search and compare the order of the
  results to the order specified in the test. Print messages to the REPL."
  [anomaly-test search-params print-results]
  (let [search-results (perform-search anomaly-test search-params)
        test-concept-ids (string/split (:concept-ids anomaly-test) #",")
        all-result-ids (map :id (:entry (:feed search-results)))
        result-ids (filter #(contains? (set test-concept-ids) %) all-result-ids)]
    (reporter/analyze-search-results anomaly-test test-concept-ids result-ids print-results)))

(defn- perform-tests
  "Read the anomaly test CSV and perform each test"
  [anomaly-filename search-params print-results]
  (for [tests-by-anomaly (sort core/string-key-to-int-sort
                               (group-by :anomaly (core/read-anomaly-test-csv anomaly-filename)))
          :let [test-count (count (val tests-by-anomaly))]]
    (do
      (when print-results
        (println (format "Anomaly %s %s"
                         (key tests-by-anomaly)
                         (if (> test-count 1) (format "(%s tests)" test-count) ""))))
      (doall
        (for [individual-test (val tests-by-anomaly)]
          (perform-search-test individual-test search-params print-results))))))

(defn run-anomaly-tests
  "Run all of the anomaly tests from the CSV file"
  ([filename args search-params]
   (run-anomaly-tests filename args search-params true))
  ([filename args search-params print-results]
   (let [test-results (flatten (doall (perform-tests filename search-params print-results)))]
     (when print-results
       (println "Running tests")
       (reporter/print-result-summary test-results)
       (println "Logging test results")
       (result-logger/log-test-run test-results args))
     test-results)))

(defmulti test-setup
  "Reset the system, ingest community usage metrics and ingest all of the test data. Right
  now there is only a default implementation because we don't separate out which test files
  are needed for the provider and edsc anomaly test cases separately."
  (fn [test-type]
    test-type))

(defmethod test-setup :default
  [_]
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
   (test-setup :provider)
   (run-anomaly-tests core/provider-anomaly-filename args search-params)
   nil)) ; Return nil so the return value is not printed to the REPL

(defn edsc-relevancy-test
  "Reset the system, ingest all of the test data, and perform the searches from
  the EDSC anomaly testing CSV. Can specify additional search params to append to the
  search via search-params"
  ([args]
   (edsc-relevancy-test args nil))
  ([args search-params]
   (test-setup :edsc)
   (run-anomaly-tests core/edsc-anomaly-filename args search-params)
   nil)) ; Return nil so the return value is not printed to the REPL

(comment
 (relevancy-test ["-log-run-description" "Base Run"]))
