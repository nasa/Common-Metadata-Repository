(ns cmr.search.services.parameters.converters.collection-query
  "Contains functions for converting query parameters to collection query condition"
  (:require [clojure.set :as set]
            [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.params :as p]))

;; Converts parameter and values into collection query condition
(defmethod p/parameter->condition :collection-query
  [context concept-type param value options]
  (qm/->CollectionQueryCondition (p/parameter->condition context :collection param value options)))
