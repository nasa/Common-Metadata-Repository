(ns cmr.elastic-utils.test.query-order-by-expense
  "Tests for the query-order-by-expense."
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.services.search.query-model :as q-mod]
   [cmr.elastic-utils.search.es-query-order-by-expense :as q-exp]
   [cmr.elastic-utils.test.helpers :refer [and-conds generic or-conds]]))

(defn script-cond
  [script-name]
  (q-mod/map->ScriptCondition {:script script-name}))

(defn range-cond
  [field]
  (q-mod/numeric-range-condition field 0 100))

(deftest order-by-expense-test
  (testing "query with and group with script, range, and term conditions"
    (let [query (q-mod/query {:condition (and-conds (script-cond "script1")
                                                (range-cond "numeric1")
                                                (generic "string1"))})
          expected (q-mod/query {:condition (and-conds (generic "string1")
                                                   (range-cond "numeric1")
                                                   (script-cond "script1"))})]
      (is (= expected (q-exp/order-conditions query)))))
  (testing "nested condition groups"
    (let [source (and-conds (or-conds (range-cond "numeric1")
                                      (generic "string1"))
                            (or-conds (script-cond "script1")
                                      (generic "string2"))
                            (generic "string3")
                            (or-conds (generic "string4")
                                      (generic "string5")))
          expected (and-conds (generic "string3")
                              (or-conds (generic "string4")
                                        (generic "string5"))
                              (or-conds (generic "string1")
                                        (range-cond "numeric1"))
                              (or-conds (generic "string2")
                                        (script-cond "script1")))]
      (is (= expected (q-exp/order-conditions source))))))
