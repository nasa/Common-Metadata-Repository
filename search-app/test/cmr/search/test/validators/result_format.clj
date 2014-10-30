(ns cmr.search.test.validators.result-format
  "Contains tests for validating supported result format by concept"
  (:require [clojure.test :refer :all]
            [cmr.search.validators.validation :as v]
            [cmr.search.models.query :as q]))


(def sample-query
  {:concept-type :collection,
   :condition
   {:field :concept-id,
    :value "c1",
    :case-sensitive? true,
    :pattern? false},
   :page-size 10,
   :page-num 1,
   :sort-keys
   [{:field :provider-id, :order :asc}
    {:field :start-date, :order :asc}],
   :result-format :xml,
   :echo-compatible? false,
   :pretty? false})

(deftest validate-supported-result-format-test
  (testing "valid result formats"
    (are [errors concept-type id result-format]
         (= errors (v/validate (q/map->Query
                                 (-> sample-query
                                     (assoc-in [:concept-type] concept-type)
                                     (assoc-in [:condition :value] id)
                                     (assoc-in [:result-format] result-format)))))
         [] :collection "C1" :json
         [] :granule "G1" :json
         [] :collection "C1" :atom
         [] :granule "G1" :atom
         [] :collection "C1" :dif))
  (testing "invalid result format"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"The mime type \[application\/dif\+xml\] is not supported."
                          (v/validate (q/map->Query
                                        (-> sample-query
                                            (assoc-in [:concept-type] :granule)
                                            (assoc-in [:condition :value] "g1")
                                            (assoc-in [:result-format] :dif))))))))

