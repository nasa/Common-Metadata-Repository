(ns search-relevancy-test.core
 (:require
  [camel-snake-kebab.core :as csk]
  [cheshire.core :as json]
  [clj-http.client :as client]
  [clojure.data.csv :as csv]
  [clojure.java.io :as io]
  [clojure.string :as string]
  [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
  [cmr.system-int-test.utils.index-util :as index]
  [cmr.system-int-test.utils.ingest-util :as ingest-util]))

(def base-search-path
  "http://localhost:3003/collections")

(def test-collection-formats
 [:iso-smap :echo10 :dif10])

(defn- provider-guids
  "Return a list of guid/provider pairs given a list of provider strings"
  [providers]
  (apply merge
    (for [provider providers]
      {(str "provguid" provider) provider})))

(defn- create-providers
 "Reset and create a provider for each provider found in the test files"
 [test-files]
 (dev-sys-util/reset)
 (let [providers (keys (group-by :provider test-files))
       provider-guids (provider-guids providers)]
   (ingest-util/setup-providers provider-guids)))

(defn- test-files-for-format
  "Returns a set of test collection files in the given format."
  [metadata-format]
  (seq (.listFiles (io/file (io/resource (str "test_files/" (name metadata-format)))))))

(defn- test-files
  "Loops through test files for each format and extracts the file name, provider id,
  and concept id. Returns a list of files with fields extracted."
  []
  (for [format test-collection-formats
        file (test-files-for-format format)
        :let [concept-id (string/replace (.getName file) #"[.][^.]+$" "")]]
    {:file file
     :concept-id concept-id
     :format format
     :provider (string/replace concept-id #".*-" "")}))

(defn ingest-test-files
  "Given a map of file, format, provider, and concept-id, ingest each file"
  [test-files]
  (doseq [test-file test-files
          :let [{:keys [file concept-id provider format]} test-file]]
    (-> (ingest-util/concept :collection provider concept-id format (slurp file))
        (assoc :concept-id concept-id)
        ingest-util/ingest-concept))
  (index/wait-until-indexed))

(defn- perform-search
  "Perform the search from the anomaly test by appending the search to the end
  of the base search path. Return results in JSON and parse."
  [anomaly-test]
  (let [response (client/get
                  (str base-search-path (:search anomaly-test))
                  {:headers {"Accept" "application/json"}})]
    (json/parse-string (:body response) true)))

(defn- analyze-search-results
  "Given the list of result concept ids in order and the order from the test,
  analyze the results to determine if the concept ids came back in the correct
  position. Print a message if not."
  [test-concept-ids result-ids]
  (let [atom-fail-count (atom 0)]
    (doseq [index (range (count test-concept-ids))
            :let [concept-id (nth test-concept-ids index)
                  result-position (.indexOf result-ids concept-id)]]
      (if (= -1 result-position)
        (do
          (println (format "Concept %s was not found in the result set" concept-id))
          (swap! atom-fail-count inc))
        (when (not= index result-position)
          (do
            (println (format (str "Concept %s should be in position %s, but is in "
                                  "position %s")
                             concept-id
                             index
                             result-position))
            (swap! atom-fail-count inc)))))
    (if (= 0 @atom-fail-count)
      (println "Test passed")
      (println (format "Test failed with %s fails out of %s"
                       @atom-fail-count
                       (count test-concept-ids))))))

(defn- perform-search-test
  "Perform the anomaly test. Perform the search and compare the order of the
  results to the order specified in the test. Print messages to the REPL."
  [anomaly-test]
  (let [search-results (perform-search anomaly-test)
        test-concept-ids (string/split (:concept-ids anomaly-test) #",")
        all-result-ids (map :id (:entry (:feed search-results)))
        result-ids (filter #(contains? (set test-concept-ids) %) all-result-ids)]
    (analyze-search-results test-concept-ids result-ids)))

(defn- csv-data->maps
  "Convert CSV data to clojure map"
  [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map csk/->kebab-case-keyword)
            repeat)
    (rest csv-data)))

(defn read-anomaly-test-csv
  "Read the anomaly test CSV and convert data to clojure map"
  []
  (->> (csv/read-csv (io/reader (io/resource "anomaly_tests.csv")))
       csv-data->maps))

(defn- perform-tests
  "Read the anomaly test CSV and perform each test"
  []
  (doseq [test (read-anomaly-test-csv)]
    (println (format "Performing test %s for anomaly %s: %s"
                     (:test test)
                     (:anomaly test)
                     (:description test)))
    (perform-search-test test)))

(defn relevancy-test
  "Reset the system, ingest all of the test data, and perform the searches from
  the anomaly testing CSV"
  []
  (let [test-files (test-files)]
    (create-providers test-files)
    (ingest-test-files test-files)
    (perform-tests)))

(comment
 (relevancy-test))
