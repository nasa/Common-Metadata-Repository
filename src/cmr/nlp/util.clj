(ns cmr.nlp.util
  (:require
    [clojure.java.io :as io]))

(def default-sentence-punct ".")

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
