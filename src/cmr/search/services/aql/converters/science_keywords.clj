(ns cmr.search.services.aql.converters.science-keywords
  "Contains functions for parsing, validating and converting scienceKeywords aql element to query conditions"
  (:require [cmr.common.xml :as cx]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.models.query :as qm]))

(defn- any-keyword-element->condition
  "Returns the query condition for the given anyKeyword element"
  [concept-type keyword-elem]
  (qm/or-conds
    [(a/string-element->condition concept-type (assoc keyword-elem :tag :categoryKeyword))
     (a/string-element->condition concept-type (assoc keyword-elem :tag :topicKeyword))
     (a/string-element->condition concept-type (assoc keyword-elem :tag :termKeyword))
     (a/string-element->condition concept-type (assoc keyword-elem :tag :variableLevel1Keyword))
     (a/string-element->condition concept-type (assoc keyword-elem :tag :variableLevel2Keyword))
     (a/string-element->condition concept-type (assoc keyword-elem :tag :variableLevel3Keyword))
     (a/string-element->condition concept-type (assoc keyword-elem :tag :detailedVariableKeyword))]))

(defn- keyword-element->condition
  "Returns the query condition of the given keyword element"
  [concept-type keyword-elem]
  (if (= :anyKeyword (:tag keyword-elem))
    (any-keyword-element->condition concept-type keyword-elem)
    (a/string-element->condition concept-type keyword-elem)))

(defn- science-keyword-element->conditions
  "Returns the query conditions of the given scienceKeyword element"
  [concept-type science-keyword]
  (let [keyword-elems (:content science-keyword)]
    (qm/nested-condition
      :science-keywords
      (qm/and-conds
        (map (partial keyword-element->condition concept-type) keyword-elems)))))

;; Converts scienceKeywords element into query condition, returns the converted condition
(defmethod a/element->condition :science-keywords
  [concept-type element]
  (let [science-keywords (cx/elements-at-path element [:scienceKeyword])
        operator (get-in element [:attrs :operator])
        conditions (map (partial science-keyword-element->conditions concept-type) science-keywords)]
    (if (= "OR" operator)
      (qm/or-conds conditions)
      (qm/and-conds conditions))))