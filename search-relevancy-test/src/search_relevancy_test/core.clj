(ns search-relevancy-test.core
  "Core functions used in other namespaces."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as string]))

(def provider-anomaly-filename
  "Provider anomaly filename"
  "anomaly_tests.csv")

(def edsc-anomaly-filename
  "EDSC user anomaly filename."
  "edsc_anomaly_tests.csv")

(def top-n-anomaly-filename
  "Top N tests anomaly filename."
  "top_n_tests.csv")

(def test-collection-formats
 [:iso-smap :echo10 :dif10 :dif :iso19115 :umm-json])

(defn string-key-to-int-sort
  "Sorts by comparing as integers."
  [v1 v2]
  (< (Integer/parseInt (key v1))
     (Integer/parseInt (key v2))))

(defn- test-files-for-format
  "Returns a set of test collection files in the given format."
  [metadata-format]
  (when-let [file (io/file (io/resource (str "test_files/" (name metadata-format))))]
    (seq (.listFiles file))))

(defn test-files
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

(defn- csv-data->maps
  "Convert CSV data to clojure map"
  [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map csk/->kebab-case-keyword)
            repeat)
    (rest csv-data)))

(defn read-csv
  "Read the CSV and convert data to clojure map"
  [csv-file]
  (->> (io/resource csv-file)
       io/reader
       csv/read-csv
       csv-data->maps))

(defn read-anomaly-test-csv
  "Read the anomaly test CSV and convert data to clojure map"
  [filename]
  (read-csv filename))

(defn get-argument-value
  "Get the value of the argument or nil if the argument does not exist"
  [args arg-name]
  (when args
    (let [arg-index (.indexOf args arg-name)]
      (when (> arg-index -1)
        (nth args (inc arg-index))))))
