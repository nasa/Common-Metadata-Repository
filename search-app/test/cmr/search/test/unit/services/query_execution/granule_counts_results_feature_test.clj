(ns cmr.search.test.unit.services.query-execution.granule-counts-results-feature-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.services.search.query-model :as q]
   [cmr.search.models.query :as qm]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]))

(defn- make-spatial-condition
  "Creates a SpatialCondition with a simple polygon"
  [lon lat]
  (let [points [(p/point lon lat)
                (p/point (+ lon 10) lat)
                (p/point (+ lon 10) (+ lat 10))
                (p/point lon (+ lat 10))
                (p/point lon lat)]
        ring (gr/ring points)
        polygon (poly/polygon :geodetic [ring])]
    (qm/->SpatialCondition polygon)))

(deftest is-spatial-or-group-test
  (testing "Returns true for OR group of SpatialConditions"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          or-group (gc/or-conds [cond1 cond2])]
      (is (true? (#'gcrf/is-spatial-or-group? or-group)))))

  (testing "Returns false for AND group of SpatialConditions"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          and-group (gc/and-conds [cond1 cond2])]
      (is (false? (#'gcrf/is-spatial-or-group? and-group)))))

  (testing "Returns false for OR group with non-SpatialConditions"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (q/string-condition :entry-title "test")
          or-group (gc/or-conds [cond1 cond2])]
      (is (false? (#'gcrf/is-spatial-or-group? or-group)))))

  (testing "Returns false for single SpatialCondition"
    (let [cond1 (make-spatial-condition 10 0)]
      (is (false? (#'gcrf/is-spatial-or-group? cond1))))))

(deftest extract-spatial-conditions-preserves-or-groups-test
  (testing "Extracts OR group of SpatialConditions preserving structure"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          or-group (gc/or-conds [cond1 cond2])
          query (q/query {:concept-type :collection
                          :condition or-group})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [or-group] result))))

  (testing "Extracts OR group nested with non-spatial conditions"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          or-group (gc/or-conds [cond1 cond2])
          title-cond (q/string-condition :entry-title "test")
          provider-cond (q/string-condition :provider "PROV1")
          combined (gc/and-conds [title-cond or-group provider-cond])
          query (q/query {:concept-type :collection
                          :condition combined})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [or-group] result)))))

(deftest extract-spatial-conditions-single-condition-test
  (testing "Extracts single SpatialCondition"
    (let [cond1 (make-spatial-condition 10 0)
          query (q/query {:concept-type :collection
                          :condition cond1})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [cond1] result))))

  (testing "Extracts single SpatialCondition nested with non-spatial conditions"
    (let [spatial-cond (make-spatial-condition 10 0)
          title-cond (q/string-condition :entry-title "test")
          provider-cond (q/string-condition :provider "PROV1")
          combined (gc/and-conds [title-cond spatial-cond provider-cond])
          query (q/query {:concept-type :collection
                          :condition combined})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [spatial-cond] result)))))

(deftest extract-spatial-conditions-mixed-test
  (testing "Extracts both OR groups and ungrouped SpatialConditions"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          cond3 (make-spatial-condition 100 50)
          or-group (gc/or-conds [cond1 cond2])
          combined (gc/and-conds [or-group cond3])
          query (q/query {:concept-type :collection
                          :condition combined})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [or-group cond3] result))))

  (testing "Extracts OR groups and ungrouped conditions with non-spatial conditions mixed in"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          cond3 (make-spatial-condition 100 50)
          or-group (gc/or-conds [cond1 cond2])
          title-cond (q/string-condition :entry-title "test")
          provider-cond (q/string-condition :provider "PROV1")
          combined (gc/and-conds [title-cond or-group provider-cond cond3])
          query (q/query {:concept-type :collection
                          :condition combined})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [or-group cond3] result)))))

(deftest extract-spatial-conditions-empty-test
  (testing "Returns empty list when no spatial conditions"
    (let [cond1 (q/string-condition :entry-title "test")
          query (q/query {:concept-type :collection
                          :condition cond1})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [] result))))

  (testing "Returns empty list when only non-spatial conditions"
    (let [title-cond (q/string-condition :entry-title "test")
          provider-cond (q/string-condition :provider "PROV1")
          combined (gc/and-conds [title-cond provider-cond])
          query (q/query {:concept-type :collection
                          :condition combined})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [] result)))))

(deftest extract-spatial-conditions-with-duplicate-polygon-test
  (testing "Extracts both OR group and standalone duplicate SpatialCondition"
    (let [cond1 (make-spatial-condition 10 0)
          cond2 (make-spatial-condition 44 -25)
          cond3 (make-spatial-condition 10 0)
          or-group (gc/or-conds [cond1 cond2])
          combined (gc/and-conds [or-group cond3])
          query (q/query {:concept-type :collection
                          :condition combined})
          result (gcrf/extract-spatial-conditions query)]
      (is (= [or-group cond3] result)))))
