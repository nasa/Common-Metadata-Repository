(ns cmr.search.test.validators.attribute
  "Contains tests for validating additional attribute conditions"
  (:require [clojure.test :refer :all]
            [cmr.common-app.services.search.query-validation :as v]
            [cmr.search.validators.attribute]
            [cmr.search.services.messages.attribute-messages :as msg]
            [cmr.search.models.query :as q]))


(deftest validate-range-test
  (are [errors type minv maxv]
       (= errors (v/validate (q/map->AttributeRangeCondition
                               {:name "foo"
                                :type type
                                :min-value minv
                                :max-value maxv})))
       [] :string "a" "b"
       [] :string "a" nil
       [] :string nil "a"
       [(msg/max-must-be-greater-than-min-msg "a" "a")] :string "a" "a"
       [(msg/max-must-be-greater-than-min-msg "b" "a")] :string "b" "a"

       [] :float 0.0 1.0
       [] :float 0.0 nil
       [] :float nil 0.0
       [(msg/max-must-be-greater-than-min-msg 1.2 1.2)] :float 1.2 1.2
       [(msg/max-must-be-greater-than-min-msg 3.0 2.999999999)] :float 3.0 2.999999999))