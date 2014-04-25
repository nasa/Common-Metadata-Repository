(ns ^{:doc "Integration test for CMR collection search by campaign terms"}
  cmr.system-int-test.search.collection-campaign-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-campaign-short-names
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {:short-name "SN1"
                                                    :entry-title "MinimalCollectionV1"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:short-name "SN2"
                                                    :entry-title "OneCollectionV1"
                                                    :campaigns []}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:short-name "Another"
                                                    :entry-title "AnotherCollectionV1"
                                                    :campaigns [{:short-name "ESI"
                                                                 :long-name "Environmental Sustainability Index"}]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:short-name "One"
                                                    :entry-title "OneCollectionV1"
                                                    :campaigns [{:short-name "ESI"
                                                                 :long-name "Environmental Sustainability Index"}
                                                                {:short-name "Esi"
                                                                 :long-name "Environmental Sustainability Index"}]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:short-name "SN2"
                                                    :entry-title "SN2CollectionV1"
                                                    :campaigns [{:short-name "EVI"
                                                                 :long-name "Environmental Vulnerability Index"}
                                                                {:short-name "EPI"
                                                                 :long-name "Environmental Performance Index"}]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:short-name "Other"
                                                    :entry-title "OtherCollectionV1"
                                                    :campaigns [{:short-name "ESI"
                                                                 :long-name "Environmental Sustainability Index"}
                                                                {:short-name "EVI"
                                                                 :long-name "Environmental Vulnerability Index"}
                                                                {:short-name "EPI"
                                                                 :long-name "Environmental Performance Index"}]}))]

    (index/flush-elastic-index)

    (testing "search by single campaign term."
      (are [expected campaign-sn] (= expected (count (search/find-refs :collection {:campaign campaign-sn})))
           3 "ESI"
           2 "EVI"
           2 "EPI"
           1 "Esi"
           0 "BLAH"))
    (testing "search using redundant campaign sn terms."
      (are [expected campaign-kv] (= expected (count (search/find-refs :collection campaign-kv)))
           3 {:campaign "ESI"}
           3 {"campaign[]" ["ESI", "ESI"]}))
    (testing "case sensitivity ..."
      (are [expected campaign-kvs] (= expected (count (search/find-refs :collection campaign-kvs)))
           0 {:campaign "EpI", "options[campaign][ignore_case]" "false"}
           2 {:campaign "EPI"}
           2 {:campaign "EpI", "options[campaign][ignore_case]" "true"}))
    (testing "search by unique campaign sn terms to get max collections"
       (is (d/refs-match? [coll3 coll4 coll5 coll6]
                          (search/find-refs :collection {"campaign[]" ["ESI" "EVI" "EPI" "Esi"]}))))
    (testing "search by wild card to get max collections"
      (is (d/refs-match? [coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {:campaign "E*", "options[campaign][pattern]" "true"}))))
    (testing "search by campaign sn terms ORed"
      (are [expected campaign-kvs] (= expected (count (search/find-refs :collection campaign-kvs)))
           1 {"campaign[]" ["ESI" "EPI" "EVI"], "options[campaign][and]" "true"}
           4 {"campaign[]" ["ESI" "EPI" "EVI"], "options[campaign][and]" "false"}
           4 {"campaign[]" ["ESI" "EPI" "EVI"]}))))


