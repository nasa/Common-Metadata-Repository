(ns cmr.system-int-test.search.autocomplete-collection-facet-search-test
  "Integration tests for autocomplete collection search facets"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util
     :refer              [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest autocomplete-suggest-test
  (let [coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection {:ShortName "OneShort" :Version "V1" :EntryTitle "E1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection {:ShortName "OnlyShort" :Version "V2" :EntryTitle "E2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection {:ShortName "OneShort" :Version "V3" :EntryTitle "E3j"}))]
    (index/wait-until-indexed)

    (testing "value only search"
             (let [results (search/get-autocomplete-suggestions "foo")]
               (is (= 0 (count (:items results))))))

    (testing "value with type search"
             (let [results (search/get-autocomplete-with-type-suggestions "foo" ["instrument"])]
               (is (= 0 (count (:items results))))))

    (testing "value with multiple type search"
             (let [results (search/get-autocomplete-with-type-suggestions "foo" ["instrument" "platform"])]
               (is (= 0 (count (:items results))))))))
