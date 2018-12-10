(ns cmr.nlp.util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
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
