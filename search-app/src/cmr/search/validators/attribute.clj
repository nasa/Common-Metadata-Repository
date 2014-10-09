(ns cmr.search.validators.attribute
  "Contains functions for validating attribute condition"
  (:require [clojure.set]
            [cmr.search.models.query :as qm]
            [cmr.search.services.messages.attribute-messages :as attrib-msg]
            [cmr.search.validators.validation :as v]))


(extend-protocol v/Validator
  cmr.search.models.query.AttributeRangeCondition
  (validate
    [{:keys [min-value max-value]}]
    (if (and max-value
             min-value
             (or (= min-value max-value)
                 (not= (sort [min-value max-value]) [min-value max-value])))
      [(attrib-msg/max-must-be-greater-than-min-msg min-value max-value)]
      [])))
