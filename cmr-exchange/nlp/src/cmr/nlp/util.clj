(ns cmr.nlp.util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [taoensso.timbre :as log])
  (:import
   (org.apache.commons.csv CSVFormat CSVParser)
   (java.net URLEncoder)
   (java.text SimpleDateFormat)))

(def default-sentence-punct ".")
(def simple-format "yyyy-MM-dd")
(def cmr-temporal-format "yyyy-MM-dd'T'HH:mm:ss'Z'")

(defn ends-with-punct?
  [sentence]
  (re-matches #".*[.?!]$" sentence))

(defn close-sentence
  [sentence]
  (if (ends-with-punct? sentence)
    sentence
    (str sentence default-sentence-punct)))

(defn get-model
  [filename]
  (io/resource (format "models/%s.bin" filename)))

(defn get-geonames
  [filename]
  (io/resource (format "geonames/%s" filename)))

(defn simple-date-formatter
  []
  (new SimpleDateFormat simple-format))

(defn cmr-date-formatter
  []
  (new SimpleDateFormat cmr-temporal-format))

(defn date->cmr-date-string
  [date]
  (.format (cmr-date-formatter) date))

(defn encode-tuple
  [[k v]]
  (format "%s=%s" (URLEncoder/encode k) (URLEncoder/encode v)))

(defn join-queries
  [queries]
  (string/join "&" queries))

(defn encode-tuples
  [tuples]
  (->> tuples
       (map encode-tuple)
       join-queries))

(defn lazy-readlines
  "Lazily read potentially very large files. Produces a lazy sequence of all
  the lines in a given file.

  Note that this is used as an alternative to reading a file `with-open` in
  order to avoid the errors that come with attempting to operate on the data
  read via `with-open` out of the context of that macro."
  [filename]
  (letfn [(reader-manager [rdr]
           (lazy-seq
             (if-let [line (.readLine rdr)]
               (cons line (reader-manager rdr))
               (.close rdr))))]
    (-> filename
        io/reader
        reader-manager)))

(defn read-tabbed-line
  "This function allows one to read a tab-delimited CSV file line-by-line."
  [line]
  (try
    (-> (CSVParser/parse line CSVFormat/TDF)
        (.getRecords)
        first
        (.iterator)
        iterator-seq)
    (catch Exception ex
      (log/warnf "Unable to import line from CSV file: %s; skipping data in line '%s' ..."
                 (.getMessage ex)
                 line)
      (log/trace ex))))

(defn read-tabbed
  "Read potentially very large tab-delimited CSV files as a lazy sequence of
  parsed lines."
  [filename]
  (->> filename
       lazy-readlines
       (map read-tabbed-line)
       (remove nil?)))

(defn read-geonames
  "Lazily read the large (GB+) Geonames CSV files."
  [filename]
  (-> filename
      get-geonames
      read-tabbed))

(def default-backoff-pred #(instance? clojure.lang.ExceptionInfo %))

;; Adapted from https://lispcast.com/exponential-backoff/
(defn exponential-backoff
  "Provides a retry function with increasingly "
  ([time rate max func]
   (exponential-backoff time rate max func func))
  ([time rate max ready-func expired-func]
   (exponential-backoff
    time rate max default-backoff-pred ready-func expired-func))
  ([time rate max pred? ready-func expired-func]
   (if (>= time max)
     (expired-func)
     (try
       (ready-func)
       (catch Throwable t
         (if (pred? t)
           (do
             (Thread/sleep time)
             (exponential-backoff
              (* time rate) rate max pred? ready-func expired-func))
           (throw t)))))))
