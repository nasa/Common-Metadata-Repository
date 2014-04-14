(ns cmr.search.services.parameter-converters.collection-query
  "Contains functions for converting query parameters to collection query condition"
  (:require [clojure.set :as set]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]))

;; Converts parameter and values into collection query condition
(defmethod p/parameter->condition :collection-query
  [concept-type param value options]
  (if (sequential? value)
    (qm/->CollectionQueryCondition (qm/or-conds
                                     (map #(p/parameter->condition :collection param % options) value)))
    (qm/->CollectionQueryCondition (p/parameter->condition :collection param value options))))
