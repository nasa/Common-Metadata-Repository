(ns cmr.indexer.data.concepts.keyword-util
  "Contains functions to convert a list of field values into a keyword text for indexing into
  an elasticsearch text field."
  (:require
   [clojure.string :as string]))

(def ^:private keywords-separator-regex
  "Defines Regex to split strings with special characters into multiple words for keyword searches."
  #"[!@#$%^&()\-=_+{}\[\]|;'.,\\\"/:<>?`~* ]")

(defn- prepare-keyword-field
  [field-value]
  "Convert a string to lowercase then separate it into keywords"
  (when field-value
    (let [field-value (string/lower-case field-value)]
      (into [field-value] (string/split field-value keywords-separator-regex)))))

(defn field-values->keyword-text
  "Returns the keyword text for the given list of field values."
  [field-values]
  (->> field-values
       (mapcat prepare-keyword-field)
       set
       (string/join " ")))
