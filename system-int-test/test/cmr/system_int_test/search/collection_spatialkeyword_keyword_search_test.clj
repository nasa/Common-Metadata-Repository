(ns cmr.system-int-test.search.collection-spatialkeyword-keyword-search-test
  "This is what's left of collection_keyword_search_test that hasn't been converted to umm-spec.
   Please see comment in CMR-3895 which is ECSE-180"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are3]]
    [cmr.search.data.keywords-to-elastic :as k2e]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3" "provguid4" "PROV4" "provguid5" "PROV5"}))

(deftest search-by-keywords
  (let [coll1 (d/ingest "PROV2" (dc/collection {:entry-title "coll10"
                                                :spatial-keywords ["in out"]}))]

    (index/wait-until-indexed)

    (testing "search by keywords."
      (are3 [keyword-str items]
        (let [parameter-refs (search/find-refs :collection {:keyword keyword-str})
              json-refs (search/find-refs-with-json-query :collection {} {:keyword keyword-str})]
          (d/assert-refs-match items parameter-refs)
          (d/assert-refs-match items json-refs))

        "search by keywords"
        "in" [coll1]))

    (testing "Default boosts on fields"
      (are3 [params scores] (is (= (map #(/ % 2.0) scores)
                                   (map :score (:refs (search/find-refs :collection params)))))
        "spatial-keyword"
        {:keyword "in out"} [(k2e/get-boost nil :spatial-keyword)]))

    (testing "Specified boosts on fields"
      (are3 [params scores] (is (= (map #(/ % 2.0) scores)
                                   (map :score (:refs (search/find-refs :collection params)))))
        "spatial-keyword"
        {:keyword "in out" :boosts {:spatial-keyword 8.0}} [8.0]))))
