(ns cmr.search.services.parameters.converters.collection-query
  "Contains functions for converting query parameters to collection query condition"
  (:require
   [cmr.search.models.query :as qm]
   [cmr.common-app.services.search.params :as p]))

;; Converts parameter and values into collection query condition
(defmethod p/parameter->condition :collection-query
  [context concept-type param value options]
  (let [param-name (if (= :collection-concept-id param)
                     :concept-id
                     param)]
    (qm/->CollectionQueryCondition
     (p/parameter->condition context :collection param-name value options))))
