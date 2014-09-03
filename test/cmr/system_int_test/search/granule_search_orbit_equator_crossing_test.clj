(ns cmr.system-int-test.search.granule-search-orbit-equator-crossing-test
  "Integration test for CMR granule temporal search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.search.validators.messages :as m]
            [cmr.search.services.messages.orbit-number-messages :as on-m]
            [cmr.common.services.messages :as cm]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-granule-orbit-equator-crossing-longitude
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [ {:equator-crossing-longitude 0}]}))
        gran2 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude 90}]}))
        gran3 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude 135}]}))
        gran4 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude 180}]}))
        gran5 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -45}]}))
        gran6 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -90}]}))
        gran7 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -135}]}))
        gran8 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -180}]}))]
    (index/refresh-elastic-index)

    (testing "search by unused crossing range returns nothing"
      (let [references (search/find-refs :granule {:equator-crossing-longitude "10,20"})]
        (is (d/refs-match? [] references))))
    (testing "search by valid crossing range returns results"
      (let [references (search/find-refs :granule {:equator-crossing-longitude "10,150.2"})]
        (is (d/refs-match? [gran2 gran3] references))))
    (testing "search by valid crossing range with min > max returns proper results"
      (let [references (search/find-refs :granule {:equator-crossing-longitude "130,-170"})]
        (is (d/refs-match? [gran3 gran4 gran8] references))))
    (testing "search by valid crossing range only min-value returns results"
      (let [references (search/find-refs :granule {:equator-crossing-longitude "120.5,"})]
        (is (d/refs-match? [gran3 gran4] references))))
    (testing "search by valid crossing range only max-value returns results"
      (let [references (search/find-refs :granule {:equator-crossing-longitude ",95.5"})]
        (is (d/refs-match? [gran1 gran2 gran5 gran6 gran7 gran8] references))))
    (testing "non-numeric value in range"
      (let [{:keys [status errors]} (search/find-refs :granule {:equator-crossing-longitude "1,X"})]
        (is (= 400 status))
        (is (= errors [(cm/invalid-msg java.lang.Double "X")]))))
    (testing "catalog-rest-style-equator-crossing-longitude full range"
      (let [references (search/find-refs :granule {"equator-crossing-longitude[minValue]" "10"
                                                   "equator-crossing-longitude[maxValue]" "150"})]
        (is (d/refs-match? [gran2 gran3] references))))
    (testing "catalog-rest-style-orbit-number min range"
      (let [references (search/find-refs :granule {"equator-crossing-longitude[minValue]" "120.5"})]
        (is (d/refs-match? [gran3 gran4] references))))
    (testing "catalog-rest-style-orbit-number max range"
      (let [references (search/find-refs :granule {"equator-crossing-longitude[maxValue]" "95.5"})]
        (is (d/refs-match? [gran1 gran2 gran5 gran6 gran7 gran8] references))))
    (testing "non-numeric value in catalog rest style range"
      (let [{:keys [status errors]} (search/find-refs :granule {"equator-crossing-longitude[maxValue]" "X"})]
        (is (= 400 status))
        (is (= errors [(cm/invalid-msg java.lang.Double "X")]))))

    (testing "search by orbit equator crossing with aql"
      (are [items crossing-range]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule
                                                     [{:equatorCrossingLongitude crossing-range}]))

           [] [10 20]
           [gran2 gran3] [10 150.2]
           [gran3 gran4 gran8] [130 -170]
           [gran3 gran4] [120.5 nil]
           [gran1 gran2 gran5 gran6 gran7 gran8] [nil 95.5]))))
