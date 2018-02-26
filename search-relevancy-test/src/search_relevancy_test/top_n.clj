(ns search-relevancy-test.top-n
  "Functions for running search relevancy tests of the form - I expect collection X to be in the
  top N collections returned when searching for Z."
  (:require
   [clj-http.client :as client]
   [clojure.data.xml :as xml]
   [cmr.common.config :refer [defconfig]]
   [search-relevancy-test.core :as core]
   [search-relevancy-test.reporter :as reporter]))

(defconfig cmr-search-url
  "Base URL for performing CMR searches."
  {:default "https://cmr.earthdata.nasa.gov/search"})

(def standard-params-string
  "Query parameters to use for every query"
  "page_size=2000")

(defn get-concept-id-from-reference
  "Returns a concept-id from a reference XML element."
  [reference]
  (->> reference
       (filter #(= :id (:tag %)))
       first
       :content
       first))

(defn get-position-in-references
  "Returns the position that the concept-id is found in within the references results. If not found
  in the results at all return nil."
  [test-concept-id references]
  (first
   (keep-indexed (fn [idx reference]
                   (let [concept-id (get-concept-id-from-reference reference)]
                     (when (= test-concept-id concept-id)
                       (inc idx))))
                 references)))

(defn get-xml-references
  "Returns the XML references for the given request URL."
  [request-url]
  (->> (client/get request-url)
       :body
       xml/parse-str
       :content
       (filter #(= :references (:tag %)))
       first
       :content
       (map :content)))

(defn perform-subtest
  "Performs a subtest for an anomaly test."
  [test]
  (let [url (format "%s/collections?%s&%s" (cmr-search-url) standard-params-string (:search test))
        references (get-xml-references url)
        position (get-position-in-references (:concept-id test) references)
        desired-position (Integer/parseInt (:position test))
        passed? (and (not (nil? position))
                     (<= position desired-position))
        result {:pass passed?
                :anomaly (:anomaly test)
                :test (:test test)
                :actual position
                :desired desired-position}]
    (reporter/print-top-n-test-result test result)
    result))

(defn perform-anomaly-test
  "Performs all of the subtests for a top N anomaly test."
  [anomaly->subtests]
  (let [subtests (val anomaly->subtests)]
    (reporter/print-start-of-anomaly-tests (key anomaly->subtests) (count subtests))
    (mapv perform-subtest subtests)))

(defn get-tests-from-filename
  "Returns a map of anomaly number to tests for that anomaly by parsing the CSV file for the passed
  in filename."
  [filename]
  (sort core/string-key-to-int-sort
        (group-by :anomaly (core/read-anomaly-test-csv filename))))

(defn run-top-n-tests
  "Run all of the top N tests from the CSV file"
  [filename]
  (let [top-n-tests (get-tests-from-filename filename)
        results (doall (mapcat perform-anomaly-test top-n-tests))]
    (reporter/print-top-n-results-summary results)
    results))

(comment
  (run-top-n-tests "top_n_tests.csv"))
