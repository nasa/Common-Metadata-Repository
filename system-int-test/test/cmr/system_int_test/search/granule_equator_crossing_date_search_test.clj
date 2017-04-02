(ns cmr.system-int-test.search.granule-equator-crossing-date-search-test
  "Integration test for CMR granule temporal search"
  (:require 
    [clojure.test :refer :all]
    [cmr.common.services.messages :as cm]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest invalid-equator-crossing-dates
  (are [v]
       (let [result (search/find-refs :granule {:equator-crossing-date  v})]
         (and (= 400 (:status result))
              (re-matches #"\[.*\] is not a valid datetime" (first (:errors result)))))
       "a,2011-02-01T12:00:00Z"
       "foo,alpha,a"
       ",alpha,a,b"))


(deftest granule-equator-crossing-date
  (let [coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        make-gran (fn [crossing-date]
                    (d/ingest "PROV1"
                              (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:orbit-calculated-spatial-domains [{:equator-crossing-date-time crossing-date}]})))
        g1 (make-gran "2011-02-01T12:00:00Z")
        g2 (make-gran "2011-02-02T12:00:00Z")
        g3 (make-gran "2011-02-02T12:00:01Z")
        g4 (make-gran "2011-02-02T12:00:10Z")
        g5 (make-gran "2011-02-02T12:01:00Z")
        g6 (make-gran "2011-02-03T12:10:00Z")
        g7 (make-gran "2011-02-07T12:01:00Z")
        g8 (make-gran "2011-02-07T12:01:00.100Z")
        g9 (make-gran "2011-02-08T12:00:00-04:00")]
    (index/wait-until-indexed)
    (testing "equator-crossing-date"
      (are [date-range items]
           (d/refs-match? items (search/find-refs :granule {:equator-crossing-date date-range}))
           "2011-02-01T12:00:00Z,2011-02-02T12:00:01Z" [g1 g2 g3]
           "2011-02-03T12:00:00Z," [g6 g7 g8 g9]
           ",2011-02-02T12:00:10Z" [g1 g2 g3 g4]
           "2011-02-08T12:00:00-03:00," [g9]
           "2011-02-07T12:01:00.090Z,2011-02-07T12:01:00.110Z" [g8]))
    (testing "catalog-rest style"
      (are [start-date end-date items]
           (d/refs-match? items (search/find-refs :granule {:equator-crossing-start-date start-date
                                                            :equator-crossing-end-date end-date}))
           "2011-02-01T12:00:00Z" "2011-02-02T12:00:01Z" [g1 g2 g3]
           "2011-02-03T12:00:00Z" nil [g6 g7 g8 g9]
           nil "2011-02-02T12:00:10Z" [g1 g2 g3 g4]))

    (testing "search by equator crossing date with aql"
      (are [items start-date stop-date]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule
                                                     [{:equatorCrossingDate {:start-date start-date
                                                                             :stop-date stop-date}}]))

           [g1 g2 g3] "2011-02-01T12:00:00Z" "2011-02-02T12:00:01Z"
           [g6 g7 g8 g9] "2011-02-03T12:00:00Z" nil
           [g1 g2 g3 g4] nil "2011-02-02T12:00:10Z"
           [g9] "2011-02-08T12:00:00-03:00" nil))))

