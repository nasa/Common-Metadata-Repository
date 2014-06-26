(ns cmr.system-int-test.search.collection-search-format-test
  "This tests ingesting and searching for collections in different formats."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))

;; Tests that we can ingest and find items in different formats
(deftest multi-format-search-test
  (let [c1-echo (d/ingest "PROV1" (dc/collection {:short-name "S1"
                                                  :version-id "V1"
                                                  :entry-title "ET1"})
                          :echo10)
        c2-echo (d/ingest "PROV2" (dc/collection {:short-name "S2"
                                                  :version-id "V2"
                                                  :entry-title "ET2"})
                          :echo10)
        c3-dif (d/ingest "PROV1" (dc/collection {:short-name "S3"
                                                 :version-id "V3"
                                                 :entry-title "ET3"})
                         :dif)
        c4-dif (d/ingest "PROV2" (dc/collection {:short-name "S4"
                                                 :version-id "V4"
                                                 :entry-title "ET4"})
                         :dif)
        all-colls [c1-echo c2-echo c3-dif c4-dif]]
    (index/refresh-elastic-index)

    (testing "Finding refs ingested in different formats"
      (are [search expected]
           (d/refs-match? expected (search/find-refs :collection search))

           {} all-colls
           {:short-name "S4"} [c4-dif]
           {:entry-title "ET3"} [c3-dif]
           {:version ["V3" "V2"]} [c2-echo c3-dif]))

    #_(testing "Retrieving results in echo10"
      (d/assert-metadata-results-match
        :echo10 all-colls
        (search/find-metadata :collection :echo10 {})))))


