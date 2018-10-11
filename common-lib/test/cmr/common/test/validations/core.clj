(ns cmr.common.test.validations.core
  "Contains tests for validations core"
  (:require [clojure.test :refer :all]
            [cmr.common.validations.core :as v]))

(defn always-valid-validation
  "Simulates a validation that is always successful"
  [field-path value]
  nil)

(defn always-invalid-validation
  "Simulates a validation that returns the value as an error message"
  [field-path value]
  {field-path [value]})


(defn identified-validation
  "Simulates a validation that always failed. The error message is a map with the id and the value."
  [id]
  (fn [field-path value]
    {field-path [{:id id
                  :value value}]}))

(defn even-validation
  "Fails validation for even numbers"
  [field-path v]
  (when (even? v)
    {field-path [(format "%%s %s was even" v)]}))

(defn assert-valid
  [validation value]
  (is (nil? (seq (v/validate validation value)))))

(deftest create-error-messages-test
  (testing "humanize fields"
    (are [v expected]
         (= expected (#'v/humanize-field v))
         nil nil
         :foo "Foo"
         :foo-bar "Foo Bar"
         :AdditionalAttributes "Additional Attributes"))
  (testing "create-error-messages"
    (testing "multiple item field path"
      (is (= ["Foo Bar was wrong"
              "Foo Bar was bad"
              "Foo is not so good"]
             (v/create-error-messages {[:alpha :foo-bar] ["%s was wrong"
                                                          "%s was bad"]
                                       [:alpha :foo] ["%s is not so good"]}))))
    (testing "number at end of field path"
      (is (= ["Foo was wrong"] (v/create-error-messages {[:alpha :foo 1] ["%s was wrong"]}))))))

(deftest record-validation-test
  (testing "validation success"
    (testing "values present"
      (assert-valid {:a always-valid-validation
                     :b always-valid-validation}
                    {:a 1 :b 2 :c 3}))
    (testing "values missing"
      (assert-valid {:a always-valid-validation
                     :b always-valid-validation}
                    {:a nil})))
  (testing "Fields are validated at the correct path"
    (is (= {[:b] [2], [:a] [1]}
           (v/validate {:a always-invalid-validation :b always-invalid-validation}
                       {:a 1 :b 2 :c 3}))))
  (testing "Missing fields are still validated"
    (is (= {[:b] [nil], [:a] [nil]}
           (v/validate {:a always-invalid-validation :b always-invalid-validation}
                       {}))))
  (testing "Nested records are validated"
    (is (= {[:foo :b] [2], [:foo :a] [1]}
           (v/validate {:foo {:a always-invalid-validation :b always-invalid-validation}}
                       {:foo {:a 1 :b 2 :c 3}})))))

(deftest sequence-validations-test
  (testing "validation success"
    (assert-valid [always-valid-validation
                   always-valid-validation
                   always-valid-validation] 5))
  (testing "every validation is applied and all errors are captured"
    (is (= {[:a] [{:id 1, :value 1} {:id 2, :value 1}]}
           (v/validate {:a [always-valid-validation
                            (identified-validation 1)
                            always-valid-validation
                            (identified-validation 2)
                            always-valid-validation]}
                       {:a 1}))))
  (testing "short circuiting validations"
    (is (= {[:a] [{:id 1, :value 1}]}
           (v/validate {:a (v/first-failing
                             always-valid-validation
                             (identified-validation 1)
                             always-valid-validation
                             (identified-validation 2)
                             always-valid-validation)}
                       {:a 1})))))

(deftest required-validation-test
  (let [validation {:foo v/required}]
    (testing "valid values"
      (are [value] (nil? (seq (v/validate validation {:foo value})))
           true
           false
           0
           1
           ""
           "something"))
    (testing "invalid values"
      (is (= {[:foo] ["%s is required."]}
             (v/validate validation {:foo nil}))))
    (testing "error message"
      (is (= ["Foo is required."]
             (v/create-error-messages (v/validate validation {:foo nil})))))))

(deftest pre-validation-test
  (let [pre-validation-fn #(str % " (pre was here)")]
    (is (= {[:a] ["the value (pre was here)"]}
           (v/validate {:a (v/pre-validation pre-validation-fn
                                             always-invalid-validation)}
                       {:a "the value"})))))

(deftest every-validation-test
  (testing "some are failures"
    (is (= {[:a 0] ["%s 0 was even"], [:a 2] ["%s 2 was even"], [:a 4] ["%s 4 was even"]}
           (v/validate {:a (v/every even-validation)}
                       {:a (range 5)}))))
  (testing "no failures"
    (assert-valid {:a (v/every always-valid-validation)}
                  {:a (range 5)}))
  (testing "always fail"
    (is (= {[:a 0] [0], [:a 1] [1], [:a 2] [2], [:a 3] [3], [:a 4] [4]}
           (v/validate {:a (v/every always-invalid-validation)}
                       {:a (range 5)}))))
  (testing "error message"
    (is (= ["Odd Numbers 0 was even" "Odd Numbers 2 was even"]
           (v/create-error-messages
             (v/validate {:odd-numbers (v/every even-validation)}
                         {:odd-numbers (range 3)}))))))

(deftest integer-validation-test
  (testing "non integer"
    (is (= {[:a] ["%s must be an integer but was [5.0]."]}
           (v/validate {:a v/validate-integer} {:a 5.0}))))
  (testing "nil value"
    (assert-valid {:a v/validate-integer} {:a nil}))
  (testing "valid value"
    (assert-valid {:a v/validate-integer} {:a 5})))

(deftest number-validation-test
  (testing "non number"
    (is (= {[:a] ["%s must be a number but was [abc]."]}
           (v/validate {:a v/validate-number} {:a "abc"}))))
  (testing "nil value"
    (assert-valid {:a v/validate-number} {:a nil}))
  (testing "valid value"
    (assert-valid {:a v/validate-number} {:a 5})))


(deftest within-range-test
  (let [validation {:a (v/within-range "j" "p")}]
    (testing "inclusive beginning"
      (assert-valid validation {:a "j"}))
    (testing "inclusive ending"
      (assert-valid validation {:a "p"}))
    (testing "inside range"
      (assert-valid validation {:a "k"}))
    (testing "before begining"
      (is (= {[:a] ["%s must be within [j] and [p] but was [i]."]}
             (v/validate validation {:a "i"}))))
    (testing "after ending"
      (is (= {[:a] ["%s must be within [j] and [p] but was [q]."]}
             (v/validate validation {:a "q"}))))))
