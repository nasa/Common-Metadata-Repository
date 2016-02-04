(ns cmr.system-int-test.search.granule-orbit-number-search-test
  "Integration test for CMR granule orbit number search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common-app.services.search.validators.messages :as m]
            [cmr.search.services.messages.orbit-number-messages :as on-m]
            [cmr.common.services.messages :as cm]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-granule-orbit-number
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [ {:orbit-number  1
                                                                          :start-orbit-number 1.0
                                                                          :stop-orbit-number 1.0
                                                                          :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))
        gran2 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:orbit-number  1
                                                                         :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))
        gran3 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:start-orbit-number 1.0
                                                                         :stop-orbit-number 1.0
                                                                         :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))
        gran4 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:orbit-number  2
                                                                         :start-orbit-number 2.0
                                                                         :stop-orbit-number 3.0
                                                                         :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))
        gran5 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:start-orbit-number 2.7
                                                                         :stop-orbit-number 3.5
                                                                         :equator-crossing-longitude 0
                                                                         :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))
        gran6 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:start-orbit-number 4.0
                                                                         :stop-orbit-number 5.0
                                                                         :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))
        gran7 (d/ingest "PROV1"
                        (dg/granule coll1
                                    {:orbit-calculated-spatial-domains [{:orbital-model-name "OrbitalModelName"
                                                                         :start-orbit-number 7.0
                                                                         :stop-orbit-number 10.0
                                                                         :equator-crossing-longitude 0
                                                                         :equator-crossing-date-time "2011-02-01T12:00:00Z"}]}))]
    (index/wait-until-indexed)

    (testing "search by exact orbit number"
      (are [items orbit-range]
           (d/refs-match? items (search/find-refs :granule {:orbit-number orbit-range}))

           ;; search by exact orbit number
           [gran1 gran2 gran3] "1"
           ;; search by orbit number range
           [gran1, gran2, gran3, gran4] "1,2"
           ;; search by orbit number range with rational numbers
           [gran4, gran5] "2,2.8"
           ;; search by orbit number range with min and max rational numbers
           [gran6 gran7] "4.5,7.7"
           ;; search by orbit number range inside
           [gran7] "8,9"
           ;; search by unused orbit number returns nothing
           [] "15"
           ;; search by unused orbit number range returns nothing
           [] "17,18"
           ;; search by min value
           [gran5 gran6 gran7] "3.5,"
           ;; search by max value
           [gran1 gran2 gran3] ",1"))

    (testing "invalid orbit number range"
      (let [{:keys [status errors]} (search/find-refs :granule {:orbit-number "2,1"})]
        (is (= 400 status))
        (is (= errors [(m/min-value-greater-than-max 2.0 1.0)]))))
    (testing "non-numeric orbit-number"
      (let [{:keys [status errors]} (search/find-refs :granule {:orbit-number "ABC"})]
        (is (= 400 status))
        (is (= errors [(on-m/invalid-orbit-number-msg) (cm/invalid-msg java.lang.Double "ABC")]))))
    (testing "non-numeric orbit-number in range"
      (let [{:keys [status errors]} (search/find-refs :granule {:orbit-number "1,X"})]
        (is (= 400 status))
        (is (= errors [(on-m/invalid-orbit-number-msg) (cm/invalid-msg java.lang.Double "X")]))))
    (testing "catalog-rest-style-orbit-number"
      (let [references (search/find-refs :granule {"orbit-number[value]" "1"})]
        (is (d/refs-match? [gran1 gran2 gran3] references))))
    (testing "catalog-rest-style-orbit-number full range"
      (let [references (search/find-refs :granule {"orbit-number[minValue]" "1"
                                                   "orbit-number[maxValue]" "2"})]
        (is (d/refs-match? [gran1 gran2 gran3 gran4] references))))
    (testing "catalog-rest-style-orbit-number min range"
      (let [references (search/find-refs :granule {"orbit-number[minValue]" "3.5"})]
        (is (d/refs-match? [gran5 gran6 gran7] references))))
    (testing "catalog-rest-style-orbit-number max range"
      (let [references (search/find-refs :granule {"orbit-number[maxValue]" "1"})]
        (is (d/refs-match? [gran1 gran2 gran3] references))))

    (testing "search by orbit number with aql"
      (are [items orbit-range]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule
                                                     [{:orbitNumber orbit-range}]))

           ;; search by exact orbit number
           [gran1 gran2 gran3] 1
           [gran1 gran2 gran3] "'1'"
           ;; search by orbit number range
           [gran1, gran2, gran3, gran4] [1 2]
           [gran1, gran2, gran3, gran4] ["'1'" "'2'"]
           ;; search by orbit number range with rational numbers
           [gran4, gran5] [2 2.8]
           ;; search by orbit number range with min and max rational numbers
           [gran6 gran7] [4.5 7.7]
           ;; search by orbit number range inside
           [gran7] [8 9]
           ;; search by unused orbit number returns nothing
           [] 15
           ;; search by unused orbit number range returns nothing
           [] [17 18]
           ;; search by min value
           [gran5 gran6 gran7] [3.5 nil]
           ;; search by max value
           [gran1 gran2 gran3] [nil 1]))))

