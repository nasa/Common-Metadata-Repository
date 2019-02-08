(ns cmr.metadata-db.test.data.oracle.sql-helper
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]))

(deftest find-params->sql-clause-test
  (testing "only allows valid param names to prevent sql-injection"
    (are [keystring] (sh/find-params->sql-clause {(keyword keystring) 1})
         "a" "a1" "A" "A1" "A_1" "b123__dkA" "a-b")
    (are [keystring] (thrown? Exception (sh/find-params->sql-clause {(keyword keystring) 1}))
         "a;b" "a&b" "a!b"))
  (testing "converting single parameter"
    (is (= `(= :a 5)
           (sh/find-params->sql-clause {:a 5}))))
  (testing "converting multiple parameters"
    (is (= `(and (= :a 5) (= :b "bravo"))
           (sh/find-params->sql-clause {:a 5 :b "bravo"})))))
