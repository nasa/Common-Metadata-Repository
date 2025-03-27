(ns cmr.system-int-test.search.granule.granule-search-entry-id-test
  "Integration tests for searching by collection entry_id"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-entry-id
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:ShortName "OneShort" :Version "V1" :EntryTitle "E1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:ShortName "OneShort" :Version "V2" :EntryTitle "E2"}))
        coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:ShortName "AnotherS" :Version "V3" :EntryTitle "E3"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:ShortName "AnotherT" :Version "V4" :EntryTitle "E4"}))
        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:ShortName "AnotherST" :Version "V5" :EntryTitle "E5"}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:ShortName "OneShort" :Version "V6" :EntryTitle "E6"}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll3 (:concept-id coll3) {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll4 (:concept-id coll4) {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll5 (:concept-id coll5) {:granule-ur "Granule5"}))
        gran6 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll6 (:concept-id coll6) {:granule-ur "Granule6"}))]
    (index/wait-until-indexed)

    (testing "search granule by collection entry-id on"
      (are3 [items entry-ids options]
        (let [params (merge options {:entry_id entry-ids})]
          (d/refs-match? items (search/find-refs :granule params)))

        "non-existent collection"
        [] "NON_EXISTENT" {}

        "a single collection"
        [gran1] "OneShort_V1" {}

        "a single collection without a version (should use short name instead)"
        [] "OnlyShort" {}

        "multiple existing collections in a single provider"
        [gran3 gran4] ["AnotherS_V3" "AnotherT_V4"] {}

        "existing collections with non-existent collection in a single provider"
        [gran3 gran4] ["AnotherS_V3" "AnotherT_V4" "NON_EXISTENT"] {}

        "multiple collections across multiple providers"
        [gran1 gran2 gran6] ["OneShort_V1" "OneShort_V2" "OneShort_V6"] {}

        "multiple collections across multiple providers with non-existent collection"
        [gran5 gran2 gran6] ["AnotherST_V5" "OneShort_V2" "OneShort_V6" "NON_EXISTENT"] {}))))
