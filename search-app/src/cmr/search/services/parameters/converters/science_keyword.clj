(ns cmr.search.services.parameters.converters.science-keyword
  "Contains functions for converting science keywords query parameters to conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.conversion :as p]))

(def science-keyword-fields
  #{:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3
    :detailed-variable})

(defn- sk-field->elastic-keyword
  "Returns the elastic keyword for the given science-keyword field"
  [science-keyword]
  (keyword (str "science-keywords." (name science-keyword))))

(defn- sk-field+value->string-condition
  "Converts a science keyword field and value into a string condition"
  [field value case-sensitive? pattern?]
  (if (sequential? value)
    (qm/string-conditions (sk-field->elastic-keyword field) value case-sensitive? pattern? :or)
    (qm/string-condition (sk-field->elastic-keyword field) value case-sensitive? pattern?)))

(defn parse-nested-science-keyword-condition
  "Converts a science keyword condition into a query model condition."
  [value case-sensitive? pattern?]
  (qm/nested-condition
    :science-keywords
    (gc/and-conds
      (map (fn [[field-name field-value]]
             (if (= :any field-name)
               (gc/or-conds
                 (map #(sk-field+value->string-condition % field-value case-sensitive? pattern?)
                      science-keyword-fields))
               (sk-field+value->string-condition field-name field-value case-sensitive? pattern?)))
           value))))

;; Converts science keywords parameter and values into conditions
(defmethod p/parameter->condition :science-keywords
  [concept-type param value options]
  (let [case-sensitive? (p/case-sensitive-field? param options)
        pattern? (p/pattern-field? param options)
        group-operation (p/group-operation param options :and)]

    (if (map? (first (vals value)))
      ;; If multiple science keywords are passed in like the following
      ;;  -> science_keywords[0][category]=foo&science_keywords[1][category]=bar
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
        group-operation
        (map #(p/parameter->condition concept-type param % options) (vals value)))
      ;; Creates the science keyword condition for a group of science keyword fields and values.
      (parse-nested-science-keyword-condition value case-sensitive? pattern?))))
