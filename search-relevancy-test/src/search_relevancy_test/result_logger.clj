(ns search-relevancy-test.result-logger
  "Functions for writing test results to file"
  (:require
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [search-relevancy-test.core :as core]
   [search-relevancy-test.reporter :as reporter]))

(def local-test-run-csv
  "local_test_runs.csv")

(def test-run-history-csv
  "test_run_history.csv")

(defn- get-last-run
  "Read the CSV file and get the data from the last run, if exists"
  [csv-file]
  (when-let [last-run (->> (core/read-csv csv-file)
                           (map #(assoc % :date (time-format/parse (:date %))))
                           (group-by :date)
                           sort
                           last)]
    (->> last-run
         val
         (group-by :run-description)
         sort
         last
         val)))

(defn- result-summary->csv-row
  "Create the CSV row data from the result summary"
  [result-summary]
  {:anomaly "SUMMARY"
   :test ""
   :description ""
   :pass (:pass result-summary)
   :discounted-cumulative-gain (:average-dcg result-summary)
   :reciprocal-rank (:mean-reciprocal-rank result-summary)
   :result-description (:result-description result-summary)})

(defn- compare-run
  "Compare the result to the given run. If they have the same anomaly # and
  test #, they match. Return true if they match and the discounted-cumulative-gain
  or reciprocal-rank are different"
  [result run]
  (and (= (:anomaly run) (:anomaly result))
       (= (:test run) (:test result))
       (or (not= (:discounted-cumulative-gain run)
                 (:discounted-cumulative-gain result))
           (not= (:reciprocal-rank run)
                 (:reciprocal-rank result)))))

(defn- changed-from-last-run
  "Determine if for the test and anomaly the result has changed from the last run.
  If so, return a string with differences from the last run. If not, return nil."
  [result last-run]
  (when-let [last-run-result (some #(when (compare-run result %) %) last-run)]
    (format "YES (DCG %s, RR: %s, Position: %s)"
            (:discounted-cumulative-gain last-run-result)
            (:reciprocal-rank last-run-result)
            (:item-position last-run-result))))

(defn- format-result-for-csv
  "Format the result for CSV run reporting. Add information and format result
  information to log the result."
  [result run-description last-run]
  (let [current-time (time/now)
        result (-> result
                   (assoc :run-description
                          (or run-description
                              (str "Test Run "
                                   (time-format/unparse (time-format/formatters :date) current-time))))
                   (assoc :date-time
                       (time-format/unparse (time-format/formatters :date-hour-minute) current-time))
                   (assoc :pass (if (:pass result) "PASS" "FAIL"))
                   (update :positional-order #(string/join " " %))
                   (update :discounted-cumulative-gain #(format "%.3f" %))
                   (update :reciprocal-rank #(format "%.3f" (double %))))]
    ;; Do this after the formatting of discounted cumulative gain and reciprocal
    ;; rank for the compare with last run.
    (assoc result :changed-from-last-run (changed-from-last-run result last-run))))

(defn write-test-run-to-csv
  "Write the run results to the given CSV file. Compare this run with the results
  from the last run."
  [csv-file-name results run-description]
  (let [result-summary-row (result-summary->csv-row
                            (reporter/generate-result-summary results))
        columns [:run-description :date-time :anomaly :test :description
                 :pass :result-description :positional-order
                 :discounted-cumulative-gain :reciprocal-rank :changed-from-last-run]
        last-run (get-last-run csv-file-name)
        rows (->> result-summary-row
                  (conj (vec results)) ;; Add summary row to end
                  (map #(format-result-for-csv % run-description last-run))
                  (mapv #(mapv % columns))
                  (cons "\n"))] ;; Add newline in the beginning to separate runs
     (with-open [csv-file (io/writer (io/resource csv-file-name) :append true)]
       (csv/write-csv csv-file rows))))

(defn log-test-run
  "Log the test run to CSV. If we have a run description in the arguments, write
  to the test run history CSV. Either way log the local run."
  [results args]
  (let [run-description (core/get-argument-value args "-log-run-description")
        log-history (core/get-argument-value args "-log-history")
        log-history (if (some? log-history)
                      (Boolean/parseBoolean log-history)
                      false)]
    (when log-history
      (write-test-run-to-csv test-run-history-csv results run-description))
    (write-test-run-to-csv local-test-run-csv results run-description)))
