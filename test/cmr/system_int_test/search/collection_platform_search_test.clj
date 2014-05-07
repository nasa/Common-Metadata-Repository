(ns cmr.system-int-test.search.collection-platform-search-test
  "Integration test for CMR collection search by platform short-names"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-platform-short-names
  (let [p1 (dc/platform "platform_Sn A")
        p2 (dc/platform "platform_Sn a")
        p3 (dc/platform "platform_SnA")
        p4 (dc/platform "platform_Snx")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {}))]

    (index/flush-elastic-index)

    (testing "search by platfrom, single value"
      (are [platform-sn items] (d/refs-match? items (search/find-refs :collection {:platform platform-sn}))
           "platform_Sn A" [coll1 coll2]
           "BLAH" []))
    (testing "search by platfrom, multiple values"
      (is (d/refs-match? [coll1 coll2 coll4]
                         (search/find-refs :collection {"platform[]" ["platform_SnA" "platform_Sn A"]}))))
    (testing "search by platfrom, ignore case true"
      (is (d/refs-match? [coll1 coll2 coll3]
                         (search/find-refs :collection {"platform[]" ["platform_Sn A"]
                                                        "options[platform][ignore-case]" "true"}))))
    (testing "search by platfrom, ignore case false"
      (is (d/refs-match? [coll1 coll2]
                         (search/find-refs :collection {"platform[]" ["platform_Sn A"]
                                                        "options[platform][ignore-case]" "false"}))))
    (testing "search by platfrom, wildcard *"
      (is (d/refs-match? [coll1 coll2 coll3]
                         (search/find-refs :collection {"platform[]" ["platform_Sn *"]
                                                        "options[platform][pattern]" "true"}))))
    (testing "search by platfrom, wildcard ?"
      (is (d/refs-match? [coll4 coll5]
                         (search/find-refs :collection {"platform[]" ["platform_Sn?"]
                                                        "options[platform][pattern]" "true"}))))
    (testing "search by platfrom, options :or."
      (is (d/refs-match? [coll1 coll2 coll3]
                         (search/find-refs :collection {"platform[]" ["platform_Sn a" "platform_Sn A"]
                                                        "options[platform][or]" "true"}))))
    (testing "search by platfrom, options :and."
      (is (d/refs-match? [coll2]
                         (search/find-refs :collection {"platform[]" ["platform_Sn a" "platform_Sn A"]
                                                        "options[platform][and]" "true"}))))))
