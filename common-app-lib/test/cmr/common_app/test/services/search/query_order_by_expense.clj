(ns cmr.common-app.test.services.search.query-order-by-expense
  (:require [clojure.test :refer :all]
            [cmr.common-app.test.services.search.helpers :refer :all]
            [cmr.common-app.services.search.query-order-by-expense :as qe]
            [cmr.common-app.services.search.query-model :as q]))

(defn script-cond
  [script-name]
  (q/map->ScriptCondition {:script script-name}))

(defn range-cond
  [field]
  (q/numeric-range-condition field 0 100))

(deftest order-by-expense-test
  (testing "query with and group with script, range, and term conditions"
    (let [query (q/query {:condition (and-conds (script-cond "script1")
                                                (range-cond "numeric1")
                                                (generic "string1"))})
          expected (q/query {:condition (and-conds (generic "string1")
                                                   (range-cond "numeric1")
                                                   (script-cond "script1"))})]
      (is (= expected (qe/order-conditions query)))))
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
      (is (= expected (qe/order-conditions source))))))