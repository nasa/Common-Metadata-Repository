(ns cmr.search.services.parameters.converters.humanizer
  "Contains functions for converting humanizer query parameters to conditions"
  (:require [clojure.string :as str]
            [cmr.common-app.services.search.query-model :as qm]
            [cmr.search.services.parameters.converters.nested-field :as nf]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.params :as p]
            [cmr.common-app.services.search.query-to-elastic :as q2e]))

;; Converts humanizer parameter and values into conditions
(defmethod p/parameter->condition :humanizer
  [_context concept-type param value options]
  (let [case-sensitive? (p/case-sensitive-field? concept-type param options)
        pattern? (p/pattern-field? concept-type param options)
        group-operation (p/group-operation param options :or)
        parent-field (q2e/query-field->elastic-field param concept-type)
        value-field (keyword (str (name parent-field) ".value"))]

    (qm/nested-condition
     parent-field
     (if (sequential? value)
       (qm/string-conditions value-field value case-sensitive? pattern? group-operation)
       (qm/string-condition value-field value case-sensitive? pattern?)))))
