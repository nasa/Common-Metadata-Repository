(ns cmr.search.services.parameter-converters.science-keyword
  "Contains functions for converting science keywords query parameters to conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]))

(def science-keyword-fields
  [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3 :detailed-variable])

;; Converts science keywords parameter and values into conditions
(defmethod p/parameter->condition :science-keywords
  [concept-type param value options]
  (if (and (> (count value) 1) (map? (first (vals value))))
    (if (= "true" (get-in options [param :or]))
      (qm/or-conds
        (map #(p/parameter->condition concept-type param % options) (vals value)))
      (qm/and-conds
        (map #(p/parameter->condition concept-type param % options) (vals value))))
    (if (map? (first (vals value)))
      (p/parameter->condition concept-type param (first (vals value)) options)
      (qm/nested-condition :science-keywords
                           (qm/and-conds
                             (map (fn [[pn pv]]
                                    (if (= :any pn)
                                      (qm/or-conds
                                        (map #(p/string-condition-with-options % pv options :science-keywords)
                                             science-keyword-fields))
                                      (p/string-condition-with-options pn pv options :science-keywords)))
                                  value))))))
