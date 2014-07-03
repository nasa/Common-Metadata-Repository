(ns cmr.search.services.parameters.converters.science-keyword
  "Contains functions for converting science keywords query parameters to conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters.conversion :as p]))

(def science-keyword-fields
  [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3 :detailed-variable])

;; Converts science keywords parameter and values into conditions
(defmethod p/parameter->condition :science-keywords
  [concept-type param value options]
  (let [case-sensitive (p/case-sensitive-field? param options)
        pattern (p/pattern-field? param options)
        group-operation (p/group-operation param options :and)]

    (if (and (> (count value) 1) (map? (first (vals value))))
      (qm/group-conds
        group-operation
        (map #(p/parameter->condition concept-type param % options) (vals value)))
      (if (map? (first (vals value)))
        (p/parameter->condition concept-type param (first (vals value)) options)
        (qm/nested-condition
          :science-keywords
          (qm/and-conds
            (map (fn [[pn pv]]
                   (if (= :any pn)
                     (qm/or-conds
                       (map #(qm/string-condition % pv case-sensitive pattern)
                            science-keyword-fields))
                     (qm/string-condition pn pv case-sensitive pattern)))
                 value)))))))

