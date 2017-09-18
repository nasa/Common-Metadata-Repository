(ns search-relevancy-test.edsc-log-parser
  "Functions for parsing the relevancy logs from EDSC in order to generate anomaly tests."
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.log :refer [warn]]
   [search-relevancy-test.core :as core]))

(defn- get-selected-index
  "Returns the index that was selected in the EDSC log line."
  [metrics]
  (Integer/parseInt (:selected_index metrics)))

(defn- get-selected-collection
  "Returns the collection concept-id that was selected from the EDSC relevancy metric."
  [metrics]
  (:selected_collection metrics))

(defn- get-top-collection
  "Returns the collection that was returned as the first result from the EDSC relevancy metric."
  [metrics]
  (first (:collections metrics)))

(defn- get-cmr-search-from-metric
  "Returns the CMR search that was used from the given EDSC relevancy metric."
  [metrics]
  (when-let [free-text (get-in metrics [:query :free_text])]
    (str "?keyword=" (string/replace (str free-text "*") " " "%20*"))))

(defn get-relevancy-json-from-line
  "Strips out everything except for the relevancy JSON from an EDSC metrics log line. Return nil
  if there are any parsing problems"
  [line]
  (try
    (-> (re-find #"\{.*\}" line)
        (json/parse-string true)
        :data)
    (catch Exception e
      (warn e)
      nil)))

(defn create-test-from-line
  "Returns an anomaly test (as a map) from the provided log line. Returns nil if no test should be
  created from the log line."
  [line test-number]
  (let [metrics (get-relevancy-json-from-line line)]
    (when (pos? (get-selected-index metrics))
      (let [selected-collection (get-selected-collection metrics)
            top-collection (get-top-collection metrics)
            cmr-search (get-cmr-search-from-metric metrics)]
        (when cmr-search
          {:search cmr-search
           :concept-ids [selected-collection top-collection]
           :test-number test-number})))))

(defn get-all-tests
  "Returns all of the tests for the given log-file"
  [log-file]
  (let [counter (atom 0)
        reader (io/reader log-file)]
    (for [line (line-seq reader)
          :let [test (create-test-from-line line (swap! counter inc))]
          :when test]
      test)))

(defn get-contradicting-tests
  "Returns any tests where there are two tests that say contradictory things (note that sense we
  are currently ignoring anything with a selected_index of 0 we can't have any contradictions.)"
  [tests]
  nil)

(defn remove-duplicate-tests
  "Returns a set of relevancy tests with any duplicates removed while still preserving the test
  order."
  [tests]
  (->> (for [test tests]
        [(select-keys test [:search :concept-ids]) test])
       (into {})
       vals
       (sort-by :test-number)))

(defn- test->anomaly-row
  "Returns a row for the anomaly CSV file "
  [test n]
  [n
   1
   (:search test)
   (string/join "," (:concept-ids test))
   (str "EDSC user anomaly line " (:test-number test))])

(def anomaly-csv-columns
  "Columns in the anomaly test file."
  ["Anomaly","Test","Search","Concept-ids","Description"])

(defn write-edsc-anomaly-csv
  "Writes out the tests to an EDSC anomaly CSV file"
  [tests output-filename]
  (let [counter (atom 0)
        row-data (for [test tests]
                   (test->anomaly-row test (swap! counter inc)))]
   (with-open [csv-file (io/writer output-filename)]
     (csv/write-csv csv-file [anomaly-csv-columns])
     (csv/write-csv csv-file row-data))))

(defn parse-edsc-logs
  "Parses the provided EDSC relevancy log file and returns search relevancy tests."
  [log-file]
  (let [all-tests (remove-duplicate-tests (get-all-tests log-file))]
    (write-edsc-anomaly-csv all-tests (str "resources/" core/edsc-anomaly-filename))
    all-tests))

(comment
 (parse-edsc-logs (io/resource "logs/EDSC_Relevancy_metrics.log")))
