(ns cmr.indexer.data.concepts.science-keyword
  "Contains functions for converting science keyword domains into a elastic documents"
  (:require [clojure.string :as str]))

(defn science-keyword->keywords
  "Converts a science keyword into a vector of terms for keyword searches"
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2 variable-level-3
                detailed-variable]} science-keyword]
    [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]))

(defn science-keywords->keywords
  "Converts the science keywords into a sequence of terms for keyword searches"
  [collection]
  (mapcat science-keyword->keywords (:science-keywords collection)))

(defn science-keyword->elastic-doc
  "Converts a science keyword into the portion going in an elastic document"
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2
                variable-level-3 detailed-variable]} science-keyword]
    {:category category
     :category.lowercase (str/lower-case category)
     :topic topic
     :topic.lowercase (str/lower-case topic)
     :term term
     :term.lowercase (str/lower-case term)
     :variable-level-1 variable-level-1
     :variable-level-1.lowercase (when variable-level-1 (str/lower-case variable-level-1))
     :variable-level-2 variable-level-2
     :variable-level-2.lowercase (when variable-level-2 (str/lower-case variable-level-2))
     :variable-level-3 variable-level-3
     :variable-level-3.lowercase (when variable-level-3 (str/lower-case variable-level-3))
     :detailed-variable detailed-variable
     :detailed-variable.lowercase (when detailed-variable (str/lower-case detailed-variable))}))

(defn science-keywords->elastic-doc
  "Converts the science keywords into a list of elastic documents"
  [collection]
  (map science-keyword->elastic-doc (:science-keywords collection)))

(defn science-keyword->facet-fields
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2
                variable-level-3 detailed-variable]} science-keyword]
    {:category category
     :topic topic
     :term term
     :variable-level-1 variable-level-1
     :variable-level-2 variable-level-2
     :variable-level-3 variable-level-3
     :detailed-variable detailed-variable}))

(defn science-keywords->facet-fields
  "Returns a map of the science keyword values in the collection for faceting storage"
  [collection]
  (reduce (fn [elastic-doc science-keyword]
            (merge-with (fn [values v]
                          (if v
                            (conj values v)
                            values))
                        elastic-doc
                        (science-keyword->facet-fields science-keyword)))
          {:category []
           :topic []
           :term []
           :variable-level-1 []
           :variable-level-2 []
           :variable-level-3 []
           :detailed-variable []}
          (:science-keywords collection)))

