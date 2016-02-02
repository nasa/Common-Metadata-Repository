(ns cmr.search.services.aql.converters.science-keywords
  "Contains functions for parsing, validating and converting scienceKeywords aql element to query conditions"
  (:require [cmr.common.xml :as cx]
            [cmr.search.services.aql.conversion :as a]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]))

(defmulti keyword-element->condition
  "Returns the query condition of the given keyword element"
  (fn [concept-type keyword-elem]
    (:tag keyword-elem)))

(defmethod keyword-element->condition :anyKeyword
  [concept-type keyword-elem]
  (gc/or-conds
    (map #(a/string-element->condition concept-type (assoc keyword-elem :tag %))
         [:categoryKeyword :topicKeyword :termKeyword :variableLevel1Keyword
          :variableLevel2Keyword :variableLevel3Keyword :detailedVariableKeyword])))

(defmethod keyword-element->condition :default
  [concept-type keyword-elem]
  (a/string-element->condition concept-type keyword-elem))

(defn- science-keyword-element->conditions
  "Returns the query conditions of the given scienceKeyword element"
  [concept-type science-keyword]
  (let [keyword-elems (:content science-keyword)]
    (qm/nested-condition
      :science-keywords
      (gc/and-conds
        (map (partial keyword-element->condition concept-type) keyword-elems)))))

;; Converts scienceKeywords element into query condition, returns the converted condition
(defmethod a/element->condition :science-keywords
  [concept-type element]
  (let [science-keywords (cx/elements-at-path element [:scienceKeyword])
        operator (get-in element [:attrs :operator])
        conditions (map (partial science-keyword-element->conditions concept-type) science-keywords)]
    (if (= "OR" operator)
      (gc/or-conds conditions)
      (gc/and-conds conditions))))