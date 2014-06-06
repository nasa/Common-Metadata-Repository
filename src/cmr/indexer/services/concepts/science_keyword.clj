(ns cmr.indexer.services.concepts.science-keyword
  "Contains functions for converting science keyword domains into a elastic documents"
  (:require [clojure.string :as s]))

(defn science-keyword->elastic-doc
  "Converts a science keyword into the portion going in an elastic document"
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2
                variable-level-3 detailed-variable]} science-keyword]
    {:category category
     :category.lowercase (s/lower-case category)
     :topic topic
     :topic.lowercase (s/lower-case topic)
     :term term
     :term.lowercase (s/lower-case term)
     :variable-level-1 variable-level-1
     :variable-level-1.lowercase (when variable-level-1 (s/lower-case variable-level-1))
     :variable-level-2 variable-level-2
     :variable-level-2.lowercase (when variable-level-2 (s/lower-case variable-level-2))
     :variable-level-3 variable-level-3
     :variable-level-3.lowercase (when variable-level-3 (s/lower-case variable-level-3))
     :detailed-variable detailed-variable
     :detailed-variable.lowercase (when detailed-variable (s/lower-case detailed-variable))}))

(defn science-keywords->elastic-doc
  "Converts the science keywords into a list of elastic documents"
  [umm-concept]
  (map science-keyword->elastic-doc (:science-keywords umm-concept)))