(ns cmr.search.data.query-to-elastic-converters.attribute
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]))

(defmulti value-condition->value-filter
  "Converts an additional attribute value condition into the nested filter to use."
  (fn [condition]
    (:type condition)))

(defmethod value-condition->value-filter :string
  [{:keys [type name value]}]
  {:term {:string-value value}})

(extend-protocol q2e/ConditionToElastic
  cmr.search.models.query.AttributeValueCondition
  (condition->elastic
    [condition]
    (let [value-filter (value-condition->value-filter condition)
          attrib-name (:name condition)]
      {:nested {:path "attributes"
                :filter {:and {:filters [{:term {:name attrib-name}}
                                         value-filter]}}}})))