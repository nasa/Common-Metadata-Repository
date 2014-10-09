(ns cmr.search.test.services.query-execution.granule-counts-results-feature
  (:require [clojure.test :refer :all]
            [cmr.search.test.models.helpers :refer :all]
            [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
            [cmr.search.models.query :as q]
            [cmr.search.models.results :as r]
            [cmr.spatial.point :as p]))

(defn spatial-cond
  "Creates a unique spatial condition"
  [n]
  (q/->SpatialCondition (p/point n n)))

(defn temporal-cond
  "Creates a unique temporal condition"
  [n]
  (q/map->TemporalCondition {:field (keyword (str "field" n))}))

(defn results-with-items
  [& concept-ids]
  (r/map->Results {:result-format :xml
                   :items (map (partial hash-map :concept-id) concept-ids)}))

(defn expected-query-with-condition
  [num-collection-ids condition]
  (q/query {:concept-type :granule
            :page-size 0
            :condition condition
            :aggregations {:granule-counts-by-collection-id
                           {:terms {:field :collection-concept-id
                                    :size num-collection-ids}}}}))

(deftest extract-granule-count-query-test
  (testing "spatial and temporal query with results"
    (let [coll-query (q/query {:condition
                               (and-conds (other)
                                          (spatial-cond 1)
                                          (temporal-cond 1)
                                          (and-conds (spatial-cond 2)
                                                     (and-conds (temporal-cond 2)
                                                                (other))))})
          results (results-with-items "C1-PROV1" "C2-PROV1")]
      (is (= (expected-query-with-condition
               2 (and-conds
                   (q/string-conditions :collection-concept-id ["C1-PROV1" "C2-PROV1"] true)
                   (spatial-cond 1)
                   (temporal-cond 1)
                   (spatial-cond 2)
                   (temporal-cond 2)))
             (gcrf/extract-granule-count-query coll-query results)))))
  (testing "spatial and temporal query with no results"
    (let [coll-query (q/query {:condition
                               (and-conds (other)
                                          (spatial-cond 1)
                                          (temporal-cond 1)
                                          (and-conds (spatial-cond 2)
                                                     (and-conds (temporal-cond 2)
                                                                (other))))})
          results (results-with-items)]
      (is (= (expected-query-with-condition 0 q/match-none)
             (gcrf/extract-granule-count-query coll-query results)))))
  (testing "non-spatial non-temporal query"
    (let [coll-query (q/query {:condition (and-conds (other) (other))})
          results (results-with-items "C1-PROV1" "C2-PROV1")]
      (is (= (expected-query-with-condition
               2 (q/string-conditions :collection-concept-id ["C1-PROV1" "C2-PROV1"] true))
             (gcrf/extract-granule-count-query coll-query results))))))