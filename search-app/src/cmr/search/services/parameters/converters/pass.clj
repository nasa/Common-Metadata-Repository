(ns cmr.search.services.parameters.converters.pass
  "Contains functions for converting passes query parameters to conditions"
  (:require
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]))

;; Converts passes parameter and values into conditions
(defmethod p/parameter->condition :passes
  [context concept-type param value options]
  (let [group-operation (p/group-operation param options :or)]

    (if (map? (first (vals value)))
      ;; If multiple passes are passed in like the following
      ;;  -> passes[0][pass]=3&passes[1][pass]=4
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
       group-operation
       (map #(p/parameter->condition context concept-type param % options) (vals value)))
      ;; Creates the passes condition for a group of passes fields and values.
      (nf/parse-nested-condition param value true false))))
