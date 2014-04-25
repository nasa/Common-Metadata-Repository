(ns cmr.search.test.services.parameter-converters.attribute
  (require [clojure.test :refer :all]
           [cmr.search.services.parameter-converters.attribute :as a]
           [cmr.search.models.query :as qm]
           [cmr.search.services.parameters :as p]
           [cmr.common.util :as u]))


(defn expected-error
  "Creates an expected error response"
  [f & args]
  {:errors [(apply f args)]})


(deftest parse-value-test
  (testing "failure cases"

    (testing "invalid number of parts"
      (are [s] (= (expected-error a/invalid-num-parts-msg)
                  (a/parse-value s))
           "string"
           "string,alpha"
           "string,alpha,min,max,"
           "string,alpha,min,max,more"
           "string,alpha,min,max,more,"))

    (testing "missing or invalid types"
      (are [s type] (= (expected-error a/invalid-type-msg type)
                  (a/parse-value s))
           ",alpha,0" ""
           "foo,alpha,0" "foo"
           "string , alpha,0" "string "))

    (testing "missing or invalid values"
      (are [s type value] (= (expected-error a/invalid-value-msg type value)
                  (a/parse-value s))
           "string,alpha," "string" ""
           "float,alpha," "float" ""
           "float,alpha,a" "float" "a"
           "float,alpha,a," "float" "a"
           "float,alpha,,a" "float" "a")
      (is (= {:errors [(a/invalid-value-msg :float "b")
                       (a/invalid-value-msg :float "a")]}
             (a/parse-value "float,alpha,a,b")))))

  (testing "strings"
    (testing "single value"
      (are [string type aname value]
           (= (qm/->AttributeValueCondition type aname value)
              (a/parse-value string))

           "string,alpha,a" :string "alpha" "a"
           "string,X\\,Y\\,Z,a" :string "X,Y,Z" "a"
           "string,X\\Y\\Z,a" :string "X\\Y\\Z" "a"))
    (testing "value range"
      (are [string type aname minv maxv]
           (= (qm/->AttributeRangeCondition type aname minv maxv)
              (a/parse-value string))

           "string,alpha,a,b" :string "alpha" "a" "b"
           "string,alpha,a," :string "alpha" "a" nil
           "string,alpha,,a" :string "alpha" nil "a"))))


(deftest parameter->condition-test
  (testing "single value condition"
    (is (= (qm/->AttributeValueCondition :string "alpha" "a")
           (p/parameter->condition :granule :attribute ["string,alpha,a"] {}))))
  (testing "single range condition"
    (is (= (qm/->AttributeRangeCondition :string "alpha" "a" "b")
           (p/parameter->condition :granule :attribute ["string,alpha,a,b"] {})))))

;; TODO Add test for multiple
