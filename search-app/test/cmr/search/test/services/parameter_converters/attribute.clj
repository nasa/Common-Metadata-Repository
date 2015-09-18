(ns cmr.search.test.services.parameter-converters.attribute
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [cmr.search.services.parameters.converters.attribute :as a]
            [cmr.search.services.messages.attribute-messages :as msg]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.conversion :as p]
            [cmr.common.util :as u]))

(defn expected-error
  "Creates an expected error response"
  [f & args]
  {:errors [(apply f args)]})

(deftest parse-value-test
  (testing "failure cases"

    (testing "invalid number of parts"
      (are [s] (= (expected-error msg/invalid-num-parts-msg)
                  (a/parse-value s))
           "string,alpha"
           "string,alpha,min,max,"
           "string,alpha,min,max,more"
           "string,alpha,min,max,more,"))

    (testing "missing or invalid types"
      (are [s type] (= (expected-error msg/invalid-type-msg type)
                       (a/parse-value s))
           ",alpha,0" ""
           "foo,alpha,0" "foo"
           "string , alpha,0" "string "))

    (testing "missing or invalid values"
      (are [s type value] (= (expected-error msg/invalid-value-msg type value)
                             (a/parse-value s))
           "string,alpha," "string" nil
           "float,alpha," "float" nil
           "float,alpha,a" "float" "a"
           "float,alpha,a," "float" "a"
           "float,alpha,,a" "float" "a"
           "datetime,alpha,a" :datetime "a")
      (is (= {:errors [(msg/invalid-value-msg :float "b")
                       (msg/invalid-value-msg :float "a")]}
             (a/parse-value "float,alpha,a,b")))))

  (testing "dates"
    (testing "datetimes"
      (are [string value]
           (= (qm/->AttributeValueCondition :datetime "alpha" nil value nil)
              (a/parse-value string))
           "datetime,alpha,2000-01-01T01:02:03.123Z" (t/date-time 2000 1 1 1 2 3 123)
           "datetime,alpha,2000-01-01T01:02:03Z" (t/date-time 2000 1 1 1 2 3)
           "datetime,alpha,2000-01-01T01:02:03" (t/date-time 2000 1 1 1 2 3)))
    (testing "times"
      (are [string value]
           (= (qm/->AttributeValueCondition :time "alpha" nil value nil)
              (a/parse-value string))
           "time,alpha,01:02:03.123Z" (t/date-time 1970 1 1 1 2 3 123)
           "time,alpha,01:02:03Z" (t/date-time 1970 1 1 1 2 3)
           "time,alpha,01:02:03" (t/date-time 1970 1 1 1 2 3)))
    (testing "dates"
      (is (= (qm/->AttributeValueCondition :date "alpha" nil (t/date-time 2000 1 1) nil)
             (a/parse-value "date,alpha,2000-01-01")))))

  (testing "strings"
    (testing "single value"
      (are [string type aname value]
           (= (qm/->AttributeValueCondition type aname nil value nil)
              (a/parse-value string))

           "string,alpha,a" :string "alpha" "a"
           "string,X\\,Y\\,Z,a" :string "X,Y,Z" "a"
           "string,X\\Y\\Z,a" :string "X\\Y\\Z" "a"))
    (testing "value range"
      (are [string type aname minv maxv]
           (= (qm/->AttributeRangeCondition type aname nil minv maxv false)
              (a/parse-value string))

           "string,alpha,a,b" :string "alpha" "a" "b"
           "string,alpha,a," :string "alpha" "a" nil
           "string,alpha,,a" :string "alpha" nil "a"))))

(deftest parameter->condition-test
  (testing "name condition"
    (let [expected-cond (qm/map->AttributeNameAndGroupCondition {:name "alpha"})]
      (is (= (gc/or-conds [expected-cond (qm/->CollectionQueryCondition expected-cond)])
             (p/parameter->condition :granule :attribute ["alpha"] {})))))
  (testing "single value condition"
    (let [expected-cond (qm/->AttributeValueCondition :string "alpha" nil "a" nil)]
      (is (= (gc/or-conds [expected-cond (qm/->CollectionQueryCondition expected-cond)])
             (p/parameter->condition :granule :attribute ["string,alpha,a"] {})))))
  (testing "single range condition"
    (are [attrs options exclusive?]
         (let [expected-cond (qm/->AttributeRangeCondition :string "alpha" nil "a" "b" exclusive?)]
           (= (gc/or-conds [expected-cond (qm/->CollectionQueryCondition expected-cond)])
              (p/parameter->condition :granule :attribute attrs options)))
         ["string,alpha,a,b"] {} false
         ["string,alpha,a,b"] {:attribute {:exclude-boundary "false"}} false
         ["string,alpha,a,b"] {:attribute {:exclude-boundary "true"}} true))

  (testing "multiple conditions"
    (testing "and conditions"
      (let [strings ["string,alpha,a" "string,alpha,a,b"]
            expected-cond (gc/and-conds [(qm/->AttributeValueCondition :string "alpha" nil "a" nil)
                                         (qm/->AttributeRangeCondition :string "alpha" nil "a" "b" false)])
            expected-cond (gc/or-conds [expected-cond (qm/->CollectionQueryCondition expected-cond)])]
        (is (= expected-cond
               (p/parameter->condition :granule :attribute strings {:attribute {:or "false"}})))
        (is (= expected-cond
               (p/parameter->condition :granule :attribute strings {}))
            "Multiple attributes should default to AND.")))
    (testing "or conditions"
      (let [strings ["string,alpha,a" "string,alpha,a,b"]
            expected-cond (gc/or-conds [(qm/->AttributeValueCondition :string "alpha" nil "a" nil)
                                        (qm/->AttributeRangeCondition :string "alpha" nil "a" "b" false)])
            expected-cond (gc/or-conds [expected-cond (qm/->CollectionQueryCondition expected-cond)])]
        (is (= expected-cond
               (p/parameter->condition :granule :attribute strings {:attribute {:or "true"}})))))))
