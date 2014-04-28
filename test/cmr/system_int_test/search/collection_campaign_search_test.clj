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
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:campaigns []}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:campaigns [{:short-name "ESI"
                                                                 :long-name "Environmental Sustainability Index"}]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:campaigns [{:short-name "ESI"
                                                                 :long-name "Environmental Sustainability Index"}
                                                                {:short-name "Esi"
                                                                 :long-name "Environmental Sustainability Index"}]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:campaigns [{:short-name "EVI"
                                                                 :long-name "Environmental Vulnerability Index"}
                                                                {:short-name "EPI"
                                                                 :long-name "Environmental Performance Index"}]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:campaigns [{:short-name "ESI"
                                                                 :long-name "Environmental Sustainability Index"}
                                                                {:short-name "EVI"
                                                                 :long-name "Environmental Vulnerability Index"}
                                                                {:short-name "EPI"
                                                                 :long-name "Environmental Performance Index"}]}))]

    (index/flush-elastic-index)

    (testing "search by single campaign term."
      (are [campaign-sn items] (d/refs-match? items (search/find-refs :collection {:campaign campaign-sn}))
           "ESI" [coll3 coll4 coll6]
           "EVI" [coll5 coll6]
           "EPI" [coll5 coll6]
           "Esi" [coll4]
           "BLAH" []))
    (testing "search using redundant campaign sn terms."
      (are [campaign-kv items] (d/refs-match? items (search/find-refs :collection campaign-kv))
           {:campaign "ESI"} [coll3 coll4 coll6]
           {"campaign[]" ["ESI", "ESI"]} [coll3 coll4 coll6]))
    (testing "case sensitivity ..."
      (are [campaign-kvs items] (d/refs-match? items (search/find-refs :collection campaign-kvs))
           {:campaign "EpI", "options[campaign][ignore_case]" "false"} []
           {:campaign "EPI"} [coll5 coll6]
           {:campaign "EpI", "options[campaign][ignore_case]" "true"} [coll5 coll6]))
    (testing "search by unique campaign sn terms to get max collections"
      (is (d/refs-match? [coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {"campaign[]" ["ESI" "EVI" "EPI" "Esi"]}))))
    (testing "search by wild card to get max collections"
      (is (d/refs-match? [coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {:campaign "E*", "options[campaign][pattern]" "true"}))))
    (testing "search by campaign sn terms ORed"
      (are [campaign-kvs items] (d/refs-match? items (search/find-refs :collection campaign-kvs))
           {"campaign[]" ["ESI" "EPI" "EVI"], "options[campaign][and]" "true"} [coll6]
           {"campaign[]" ["ESI" "EPI" "EVI"], "options[campaign][and]" "false"} [coll3 coll4 coll5 coll6]
           {"campaign[]" ["ESI" "EPI" "EVI"]} [coll3 coll4 coll5 coll6]))))

