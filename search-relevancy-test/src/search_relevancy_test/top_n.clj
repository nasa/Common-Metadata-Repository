(ns search-relevancy-test.top-n
  "Functions for running search relevancy tests of the form - I expect collection X to be in the
  top N collections returned when searching for Z."
  (:require
   [clj-http.client :as client]
   [cmr.common.config :refer [defconfig]]
   [search-relevancy-test.core :as core]
   [clojure.data.xml :as xml]))

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
                    ;  (println "Concept-id" concept-id "for reference" reference)
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

; (defn- perform-tests
;   "Read the anomaly test CSV and perform each test"
;   [anomaly-filename search-params print-results]
;   (for [tests-by-anomaly (sort core/string-key-to-int-sort
;                                (group-by :anomaly (core/read-anomaly-test-csv anomaly-filename)))
;           :let [test-count (count (val tests-by-anomaly))]]
;     (do
;       (when print-results
;         (println (format "Anomaly %s %s"
;                          (key tests-by-anomaly)
;                          (if (> test-count 1) (format "(%s tests)" test-count) ""))))
;       (doall
;         (for [individual-test (val tests-by-anomaly)]
;           (perform-search-test individual-test search-params print-results))))))


(defn print-test-result
  "Prints the test result in a way to quickly see whether a test passed or failed."
  [test-result]
  (println test-result))

(defn perform-test
  "Performs a top N test"
  [test]
  ; (println "Running test" test)
  ; (reporter/print-start-of-anomaly-tests anomaly-number number-of-subtests)
  (let [url (format "%s/collections?%s&%s" (cmr-search-url) standard-params-string (:search test))
        references (get-xml-references url)
        ; _ (println "References are" references)
        position (get-position-in-references (:concept-id test) references)
        desired-position (Integer/parseInt (:position test))
        passed? (and (not (nil? position))
                     (<= position desired-position))]
    {:pass passed?
     :anomaly (:anomaly test)
     :test (:test test)
     :actual position
     :desired desired-position}))

(defn get-tests-from-filename
  "Returns tests by parsing the CSV file for the passed in filename."
  [filename]
  (core/read-anomaly-test-csv filename))

(defn run-top-n-tests
  "Run all of the top N tests from the CSV file"
  [filename]
  (let [top-n-tests (get-tests-from-filename filename)]
    (map perform-test top-n-tests)))

(comment
  (run-top-n-tests "top_n_tests.csv"))
