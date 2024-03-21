(ns cmr.search.services.parameters.converters.humanizer
  "Contains functions for converting humanizer query parameters to conditions"
  (:require [clojure.string :as str]
            [cmr.elastic-utils.es-query-model :as qm]
            [cmr.elastic-utils.nested-field :as nf]
            [cmr.elastic-utils.es-group-query-conditions :as gc]
            [cmr.elastic-utils.es-params-converter :as p]
            [cmr.elastic-utils.es-query-to-elastic :as q2e]))

;; Converts humanizer parameter and values into conditions
(defmethod p/parameter->condition :humanizer
  [_context concept-type param value options]
  (let [case-sensitive? (p/case-sensitive-field? concept-type param options)
        pattern? (p/pattern-field? concept-type param options)
        group-operation (p/group-operation param options :or)
        parent-field (q2e/query-field->elastic-field param concept-type)
        value-field (keyword (str (name parent-field) ".value"))]
    (if (sequential? value) 
      (qm/->ConditionGroup
        group-operation
        (map #(qm/nested-condition parent-field %) 
             (map #(qm/string-condition value-field % case-sensitive? pattern?) value)))
      (qm/nested-condition
        parent-field
        (qm/string-condition value-field value case-sensitive? pattern?)))))
