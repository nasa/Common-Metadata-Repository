(ns ^{:doc "Search CMR granules by day night flag"}
  cmr.system-int-test.search.granule-search-day-night-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))


(deftest search-by-day-night
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV2" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:day-night "DAY"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:day-night "NIGHT"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:day-night "BOTH"}))
        gran4 (d/ingest "PROV2" (dg/granule coll2 {:day-night "UNSPECIFIED"}))
        gran5 (d/ingest "PROV2" (dg/granule coll2 {:day-night "DAY"}))
        gran6 (d/ingest "PROV2" (dg/granule coll2 {:day-night "DAY"}))
        gran7 (d/ingest "PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran8 (d/ingest "PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran9 (d/ingest "PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran10 (d/ingest "PROV2" (dg/granule coll2 {:day-night "UNSPECIFIED"}))]
    (index/refresh-elastic-index)

    (testing "search by day-night-flag"
      (are [items day-night options]
           (let [params (merge {:day-night-flag day-night}
                               (when options
                                 {"options[day-night-flag]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [] "FAKE" {}
           [gran1 gran5 gran6] "DAY" {}
           [gran2] "NIGHT" {}
           [gran3 gran7 gran8 gran9] "BOTH" {}
           [gran4 gran10] "UNSPECIFIED" {}
           [gran1 gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10] ["DAY", "BOTH", "UNSPECIFIED"] {}

           ;; ignore case
           [gran2] "nIgHt" {}
           [gran2] "nIgHt" {:ignore-case true}
           [] "nIgHt" {:ignore-case false}

           ;; pattern
           [gran4 gran10] "*SP*C*" {:pattern true}
           [gran2] "?I?H?" {:pattern true}

           ;; and option
           [] ["DAY", "NIGHT"] {:and true}))

    (testing "search by day-night-flag with aql"
      (are [items day-night options]
           (let [condition (merge {:dayNightFlag day-night} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1 gran5 gran6] "DAY" {}
           [gran1 gran5 gran6] "day" {}
           [gran1 gran5 gran6] "" {}
           [gran2] "NIGHT" {}
           [gran2] "night" {}
           [gran3 gran7 gran8 gran9] "BOTH" {}
           [gran3 gran7 gran8 gran9] "both" {}))

    (testing "search by day-night-flag invalid value"
      (let [{:keys [status errors]} (search/find-refs-with-aql :granule [{:dayNightFlag "wrong"}])]
        (is (= 400 status))
        (is (re-find #"AQL Query Syntax Error: " (first errors)))))))
