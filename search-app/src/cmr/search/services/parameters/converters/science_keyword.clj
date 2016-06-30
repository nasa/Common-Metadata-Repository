(ns cmr.search.services.parameters.converters.science-keyword
  "Contains functions for converting science keywords query parameters to conditions"
  (:require [clojure.string :as str]
            [cmr.search.services.parameters.converters.nested-field :as nf]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.params :as p]))

;; Converts science keywords parameter and values into conditions
(defmethod p/parameter->condition :science-keywords
  [concept-type param value options]
  (let [case-sensitive? (p/case-sensitive-field? concept-type param options)
        pattern? (p/pattern-field? concept-type param options)
        group-operation (p/group-operation param options :and)
        target-field (keyword (subs (str/replace param #"-h$" ".humanized") 1))]

    (if (map? (first (vals value)))
      ;; If multiple science keywords are passed in like the following
      ;;  -> science_keywords[0][category]=foo&science_keywords[1][category]=bar
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
        group-operation
        (map #(p/parameter->condition concept-type param % options) (vals value)))
      ;; Creates the science keyword condition for a group of science keyword fields and values.
      (nf/parse-nested-condition target-field value case-sensitive? pattern?))))
