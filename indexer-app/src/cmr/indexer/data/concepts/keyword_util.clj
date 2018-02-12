(ns cmr.indexer.data.concepts.keyword-util
  "Contains utility functions for working with keywords when adding data
  to elasticsearch for indexing."
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

(defn science-keyword->keywords
  "Converts a science keyword into a vector of terms for keyword searches."
  [science-keyword]
  (let [{category :Category
         detailed-variable :DetailedVariable
         term :Term
         topic :Topic
         variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2
         variable-level-3 :VariableLevel3} science-keyword]
    [category
     detailed-variable
     term
     topic
     variable-level-1
     variable-level-2
     variable-level-3]))
