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

(defn lazy-readlines [filename]
  (letfn [(reader-manager [rdr]
           (lazy-seq
             (if-let [line (.readLine rdr)]
               (cons line (reader-manager rdr))
               (.close rdr))))]
    (-> filename
        io/reader
        reader-manager)))

(defn read-tabbed-line
  [line]
  (try
    (-> (CSVParser/parse line CSVFormat/TDF)
        (.getRecords)
        first
        (.iterator)
        iterator-seq)
    (catch Exception ex
      (log/warnf "CSV import error: %s; skipping data in line '%s' ..."
                 (.getMessage ex)
                 line)
      (log/trace ex))))

(defn read-tabbed
  [filename]
  (->> filename
       lazy-readlines
       (map read-tabbed-line)
       (remove nil?)))

(defn read-geonames
  [filename]
  (-> filename
      get-geonames
      read-tabbed))

(def default-backoff-pred #(instance? clojure.lang.ExceptionInfo %))

;; Adapted from https://lispcast.com/exponential-backoff/
(defn exponential-backoff
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
