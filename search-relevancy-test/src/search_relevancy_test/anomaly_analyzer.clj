(ns search-relevancy-test.anomaly-analyzer
  "Functions to help analyze why a relevancy test is failing"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [search-relevancy-test.core :as core]
   [search-relevancy-test.relevancy-test :as relevancy-test]))

(defn- get-test-data
  "Get the test data from the CSV file - need the concept ids we want to look at
  and the search params. If the anomaly has multiple tests, we get the data for
  all of the tests."
  [anomaly-number file-name]
  (let [csv-data (core/read-anomaly-test-csv file-name)
        tests (filter #(= (str anomaly-number) (:anomaly %))
                      csv-data)
        concept-ids (distinct
                     (mapcat #(str/split (:concept-ids %) #",")
                             tests))]
    {:search (:search (first tests))
     :concept-ids concept-ids}))

(defn- filter-search-results
  "Filter search results by the relevant concept ids and also the data that is
  used for relevancy sorting: the score, short name, version, and processing level
  id."
  [search-results test]
  (let [collections (filter
                     #(contains? (set (:concept-ids test)) (:id %))
                     (get-in search-results [:feed :entry]))
        data (map #(select-keys % [:id :score :version_id :short_name :processing_level_id])
                  collections)]

    data))

(defn analyze-test
 "Get relevancy-related data for concepts in the test by performing the search
 and filtering by concept"
 ([anomaly-number]
  (analyze-test anomaly-number core/provider-anomaly-filename))
 ([anomaly-number file-name]
  (let [test (get-test-data anomaly-number file-name)
        search-results (relevancy-test/perform-search test nil)]
    (println "Filtered search result is: " (filter-search-results search-results test)))))

(comment
 (analyze-test 39))
