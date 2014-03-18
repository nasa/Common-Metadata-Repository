(ns cmr.search.test.models.query
  (:require [clojure.test :refer :all]
            [cmr.search.services.parameters :as p]
            [cmr.search.models.query :as q]))

;; TODO add test of and-ing and or-ing conditions
;; What should happen if the conditions list is empty?
;; - match all? or match none? or internal error?

(deftest conds-test
  (doseq [[operator f] [[:and q/and-conds] [:or q/or-conds]]]
    (testing (str operator " Conditions")
      (testing "no conditions"
        (is (thrown? Exception (f []))))
      (testing "one condition"
        (is (= (q/string-condition :a 1)
               (f [(q/string-condition :a 1)]))))
      (testing "multiple conditions"
        (let [conds [(q/string-condition :a 1)
                     (q/string-condition :b 2)]]
          (is (= (q/->ConditionGroup operator conds)
                 (f conds))))))))