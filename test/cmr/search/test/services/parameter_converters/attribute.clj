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
                  (u/remove-nil-keys (a/parse-value s)))
           "string"
           "string,alpha"
           "string,alpha,min,max,"
           "string,alpha,min,max,more"
           "string,alpha,min,max,more,"))

    (testing "missing or invalid types"
      (are [s type] (= (expected-error a/invalid-type-msg type)
                  (u/remove-nil-keys (a/parse-value s)))
           ",alpha,0" ""
           "foo,alpha,0" "foo"
           "string , alpha,0" "string "))

    (testing "missing or invalid values"
      (are [s type value] (= (expected-error a/invalid-value-msg type value)
                  (u/remove-nil-keys (a/parse-value s)))
           "string,alpha," "string" ""
           "float,alpha," "float" ""
           "float,alpha,a" "float" "a")))

  (testing "strings"
    (testing "single value"
      (are [string type aname value]
           (= {:attribute-type type
               :search-type :value
               :name aname
               :value value}
              (u/remove-nil-keys (a/parse-value string)))

           "string,alpha,a" :string "alpha" "a"
           "string,X\\,Y\\,Z,a" :string "X,Y,Z" "a"
           "string,X\\Y\\Z,a" :string "X\\Y\\Z" "a"))
    (testing "value range"
      (are [string type aname minv maxv]
           (= (u/remove-nil-keys
                {:attribute-type type
                 :search-type :range
                 :name aname
                 :min-value minv
                 :max-value maxv})
              (u/remove-nil-keys (a/parse-value string)))

           "string,alpha,a,b" :string "alpha" "a" "b"
           "string,alpha,a," :string "alpha" "a" nil
           "string,alpha,,a" :string "alpha" nil "a"))))


(deftest parameter->condition-test
  (testing "single value condition"
    (is (= [(qm/->AttributeValueCondition :string "alpha" "a")]
           (p/parameter->condition :granule :attribute ["string,alpha,a"] {}))))
  (testing "single range condition"
    (is (= [(qm/->AttributeRangeCondition :string "alpha" "a" "b")]
           (p/parameter->condition :granule :attribute ["string,alpha,a,b"] {})))))
