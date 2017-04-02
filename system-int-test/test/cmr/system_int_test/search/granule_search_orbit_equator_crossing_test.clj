(ns cmr.system-int-test.search.granule-search-orbit-equator-crossing-test
  "Integration test for CMR granule temporal search"
  (:require 
    [clojure.test :refer :all]
    [cmr.common-app.services.search.messages :as m]
    [cmr.common.services.messages :as cm]
    [cmr.search.services.messages.orbit-number-messages :as on-m]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-granule-orbit-equator-crossing-longitude
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        gran1 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [ {:equator-crossing-longitude 0}]}))
        gran2 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude 90}]}))
        gran3 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude 135}]}))
        gran4 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude 180}]}))
        gran5 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -45}]}))
        gran6 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -90}]}))
        gran7 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -135}]}))
        gran8 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection coll1 (:conept-id coll1)
                                    {:orbit-calculated-spatial-domains [{:equator-crossing-longitude -180}]}))]
    (index/wait-until-indexed)

    (testing "search by crossing range"
      (are [items crossing-range]
           (d/refs-match? items
                          (search/find-refs
                            :granule
                            {:equator-crossing-longitude crossing-range}))
           ;; no match
           [] "10,20"
           ;; single value
           [gran4] "180"
           ;; valid range
           [gran2 gran3] "10,150.2"
           ;; min > max (crossing antimeridian)
           [gran3 gran4 gran8] "130,-170"
           ;; min-value only
           [gran3 gran4] "120.5,"
           ;; max-value only
           [gran1 gran2 gran5 gran6 gran7 gran8] ",95.5"))

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
