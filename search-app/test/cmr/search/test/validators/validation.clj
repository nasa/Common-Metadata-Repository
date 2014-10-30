(ns cmr.search.test.validators.validation
  (:require [clojure.test :refer :all]
            [cmr.search.validators.validation :as v]
            [cmr.search.models.query :as q]))


(def sample-query
  {:concept-type :collection,
   :condition
   {:field :concept-id,
    :value "id1",
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
  (testing "result formats"
    (are [errors concept-type result-format]
         (= errors (v/validate (q/map->Query
                                 (assoc sample-query
                                        :concept-type concept-type
                                        :result-format result-format))))
         [] :collection :json
         [] :granule :json
         [] :collection :atom
         [] :granule :atom
         [] :collection :dif
         ["The mime type [application/dif+xml] is not supported for granules."] :granule :dif)))


