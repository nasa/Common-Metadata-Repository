(ns cmr.search.test.validators.validation
  (:require [clojure.test :refer :all]
            [cmr.common-app.services.search.query-validation :as v]
            [cmr.common-app.services.search.query-model :as q]))


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
   :echo-compatible? false})

(deftest validate-supported-result-format-test
  (testing "result formats"
    (are [errors concept-type result-format]
         (= errors (v/validate (q/map->Query
                                 (assoc sample-query
                                        :concept-type concept-type
                                        :result-format result-format))))
         [] :collection :xml
         [] :collection :json
         [] :collection :echo10
         [] :collection :dif
         [] :collection :dif10
         [] :collection :atom
         [] :collection :iso19115
         [] :collection :opendata
         ["The mime type [text/csv] is not supported for collections."] :collection :csv
         [] :collection :kml
         [] :granule :xml
         [] :granule :json
         [] :granule :echo10
         ["The mime type [application/dif+xml] is not supported for granules."] :granule :dif
         ["The mime type [application/dif10+xml] is not supported for granules."] :granule :dif10
         [] :granule :atom
         [] :granule :iso19115
         ["The mime type [application/opendata+json] is not supported for granules."] :granule :opendata
         [] :granule :csv
         [] :granule :kml)))