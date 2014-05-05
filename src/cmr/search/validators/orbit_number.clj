(ns cmr.search.validators.orbit-number
  "Contains functions for validating attribute condition"
  (:require [clojure.set]
            [cmr.search.models.query :as qm]
            [cmr.search.services.messages.orbit-number-messages :as on-msg]
            [cmr.search.validators.validation :as v]))


(extend-protocol v/Validator
  cmr.search.models.query.OrbitNumberRangeCondition
  (validate
    [{:keys [start-orbit-number-range-condition
             orbit-number-range-condition
             stop-orbit-number-range-condition]}]
    (-> []
        (into (v/validate start-orbit-number-range-condition))
        (into (v/validate orbit-number-range-condition))
        (into (v/validate stop-orbit-number-range-condition))
        (distinct))))