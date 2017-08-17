(ns cmr.search.test.services.query-walkers.update-field-resolver
  "Tests for extracting temporal ranges from queries"
  (:require
    [clj-time.core :as time]
    [clojure.test :refer :all]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common.util :refer [are3]]
    [cmr.search.models.query :as query]
    [cmr.search.services.query-walkers.update-field-resolver :as update-field-resolver]
    [cmr.search.test.models.helpers :as query-helper]))

(deftest update-field-resolver-has-field-test
  (are3 [query-condition renamed-condition]
    (do
      (testing "has field test"
        (is (= true (update-field-resolver/has-field? query-condition :foo)))
        (is (= false (update-field-resolver/has-field? query-condition :no-foo))))
      (testing "remove field test"
        (is (nil? (update-field-resolver/remove-field query-condition :foo)))
        (is (= query-condition (update-field-resolver/remove-field query-condition :no-foo))))
      (testing "rename field test"
        (is (= renamed-condition
               (update-field-resolver/rename-field query-condition :foo :new-foo)))
        (is (= query-condition
               (update-field-resolver/rename-field query-condition :no-foo :new-foo)))))

    "Nested condition"
    (qm/nested-condition "/path/to/nowhere" (qm/string-condition :foo "bar"))
    (qm/nested-condition "/path/to/nowhere" (qm/string-condition :new-foo "bar"))

    "Text condition"
    (qm/text-condition :foo "bar")
    (qm/text-condition :new-foo "bar")

    "String condition"
    (qm/string-condition :foo "bar")
    (qm/string-condition :new-foo "bar")

    "Strings condition"
    (qm/string-conditions :foo ["bar" "none"])
    (qm/string-conditions :new-foo ["bar" "none"])

    "Negated Condition"
    (qm/not-exist-condition :foo)
    (qm/not-exist-condition :new-foo)

    "Boolean condition"
    (qm/boolean-condition :foo false)
    (qm/boolean-condition :new-foo false)

    "Exist condition"
    (qm/exist-condition :foo)
    (qm/exist-condition :new-foo)

    "Missing condition"
    (qm/->MissingCondition :foo)
    (qm/->MissingCondition :new-foo)

    "Date Value condition"
    (qm/date-value-condition :foo (time/date-time 2012 07 15))
    (qm/date-value-condition :new-foo (time/date-time 2012 07 15))

    "Date range condition"
    (qm/date-range-condition :foo
                             (time/date-time 2014 01 01)
                             (time/date-time 2017 01 01))
    (qm/date-range-condition :new-foo
                             (time/date-time 2014 01 01)
                             (time/date-time 2017 01 01))

    "Numeric value condition"
    (qm/numeric-value-condition :foo 42)
    (qm/numeric-value-condition :new-foo 42)

    "Numeric range condition"
    (qm/numeric-range-condition :foo 10 30 false)
    (qm/numeric-range-condition :new-foo 10 30 false)

    "String range condition"
    (qm/string-range-condition :foo "abc" "xyz")
    (qm/string-range-condition :new-foo "abc" "xyz")

    "Related Item Query Condition"
    (qm/map->RelatedItemQueryCondition
      {:concept-type :granule
       :condition (qm/string-condition :foo "bar")
       :result-fields [:alpha :beta :foo]
       :results-to-condition-fn identity})
    (qm/map->RelatedItemQueryCondition
      {:concept-type :granule
       :condition (qm/string-condition :new-foo "bar")
       :result-fields [:alpha :beta :foo]
       :results-to-condition-fn identity})))

(deftest root-level-query-test
  (let [query (qm/query {:condition (qm/string-condition :foo "bar")})]
    (testing "Has field"
      (is (= true (update-field-resolver/has-field? query :foo)))
      (is (= false (update-field-resolver/has-field? query :no-foo))))
    (testing "Remove field"
      (is (= (qm/query {:condition qm/match-all})
             (update-field-resolver/remove-field query :foo)))
      (let [query-multiple-conds (qm/query
                                  {:condition (qm/->ConditionGroup
                                               :and
                                               [(qm/string-condition :foo "bar")
                                                (qm/not-exist-condition :alpha)])})]
        (is (= query (update-field-resolver/remove-field query-multiple-conds :alpha))))
      (is (= query (update-field-resolver/remove-field query :no-foo))))
    (testing "Rename field"
      (is (= (qm/query {:condition (qm/string-condition :new-foo "bar")})
             (update-field-resolver/rename-field query :foo :new-foo)))
      (is (= query (update-field-resolver/rename-field query :no-foo :new-foo))))))


(deftest condition-group-test
  (let [condition (qm/->ConditionGroup :and [(qm/string-condition :foo "bar")
                                             (qm/not-exist-condition :alpha)])]
    (testing "Has field"
      (is (= true (update-field-resolver/has-field? condition :foo)))
      (is (= true (update-field-resolver/has-field? condition :alpha)))
      (is (= false (update-field-resolver/has-field? condition :no-foo))))
    (testing "Remove field"
      (testing "Condition group removed when down to a single value"
        (is (= (qm/not-exist-condition :alpha)
               (update-field-resolver/remove-field condition :foo))))
      (testing "Condition group remains if there are still at least two conditions"
        (let [three-condition-group (qm/->ConditionGroup
                                     :and
                                     [(qm/string-condition :foo "bar")
                                      (qm/not-exist-condition :alpha)
                                      (qm/numeric-value-condition :beta 31)])]
          (is (= condition (update-field-resolver/remove-field three-condition-group :beta)))))
      (testing "Field that doesn't exist makes no change"
        (is (= condition (update-field-resolver/remove-field condition :no-foo)))))
    (testing "Rename field"
      (is (= (qm/->ConditionGroup :and [(qm/string-condition :foo "bar")
                                        (qm/not-exist-condition :new-alpha)])
             (update-field-resolver/rename-field condition :alpha :new-alpha)))
      (is (= condition (update-field-resolver/rename-field condition :no-alpha :new-alpha))))))

(deftest numeric-range-intersection-test
  (let [condition (qm/numeric-range-intersection-condition :alpha :omega 0 100)]
    (testing "Has field"
      (testing "min-field and max-field are matched against field"
          (is (= true (update-field-resolver/has-field? condition :alpha)))
          (is (= true (update-field-resolver/has-field? condition :omega)))
          (is (= false (update-field-resolver/has-field? condition :beta)))))
    (testing "Remove field"
      (testing "min-field and max-field are matched against field"
          (is (nil? (update-field-resolver/remove-field condition :alpha)))
          (is (nil? (update-field-resolver/remove-field condition :omega)))
          (is (= condition (update-field-resolver/remove-field condition :beta)))))
    (testing "Rename field"
      (testing "min-field and max-field are matched against field"
          (is (= (qm/numeric-range-intersection-condition :new-alpha :omega 0 100)
                 (update-field-resolver/rename-field condition :alpha :new-alpha)))
          (is (= (qm/numeric-range-intersection-condition :alpha :new-omega 0 100)
                 (update-field-resolver/rename-field condition :omega :new-omega)))
          (is (= condition (update-field-resolver/rename-field condition :beta :new-beta)))))))
