(ns cmr.system-int-test.search.collection.collection-progress-search-test
  "Integration test for CMR collection search by collection progress"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common-app.test.side-api :as side]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-collection-progress
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:CollectionProgress "ACTIVE"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:CollectionProgress "PLANNED"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:CollectionProgress "COMPLETE"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:CollectionProgress "DEPRECATED"}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:CollectionProgress "NOT PROVIDED"}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 6 {:CollectionProgress "PREPRINT"}))
        coll7 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 7 {:CollectionProgress "INREVIEW"}))
        coll8 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 8 {:CollectionProgress "SUPERSEDED"}))]

    (index/wait-until-indexed)

    (testing "collection progress parameter search"
      (are3 [items progress options]
        (let [params (merge {:collection-progress progress}
                            options)]
          (d/refs-match? items (search/find-refs :collection params)))

        "single value search"
        [coll1] "ACTIVE" nil

        "case insensitive"
        [coll2] "planned" nil

        "pattern search with RE substring"
        [coll4 coll6 coll7] "*RE*" {"options[collection-progress][pattern]" "true"}

        "OR search"
        [coll1 coll2] ["ACTIVE" "PLANNED"] nil

        "case sensitive search"
        [] "active" {"options[collection-progress][ignore-case]" "false"}

        "invalid value returns empty"
        [] "INVALID" nil))))

(deftest search-collection-progress-active-filter
  "Tests the non-operational collection filter feature flag behavior."
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:CollectionProgress "ACTIVE"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:CollectionProgress "PLANNED"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:CollectionProgress "COMPLETE"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:CollectionProgress "DEPRECATED"}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:CollectionProgress "NOT PROVIDED"}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 6 {:CollectionProgress "PREPRINT"}))
        coll7 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 7 {:CollectionProgress "INREVIEW"}))
        coll8 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 8 {:CollectionProgress "SUPERSEDED"}))
        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 coll7 coll8]
        active-colls [coll1 coll3 coll5 coll8]]

    (index/wait-until-indexed)

    (testing "flag OFF - param ignored, all collections returned"
      (util/are3 [items search]
        (d/refs-match? items (search/find-refs :collection search))

        "no params"
        all-colls {}

        "include-non-operational=false"
        all-colls {:include-non-operational "false"}

        "include-non-operational=true"
        all-colls {:include-non-operational "true"}))

    (side/eval-form `(cmr.search.config/set-enable-non-operational-collection-filter! true))

    (testing "flag ON - filter applies unless overridden"
      (util/are3 [items search]
        (d/refs-match? items (search/find-refs :collection search))

        "no params - only operational collections (excludes PLANNED, DEPRECATED, PREPRINT, INREVIEW)"
        active-colls {}

        "include-non-operational=false - only operational collections"
        active-colls {:include-non-operational "false"}

        "include-non-operational=true - all collections"
        all-colls {:include-non-operational "true"}

        "explicit collection-progress=PLANNED - PLANNED returned (filter not applied)"
        [coll2] {:collection-progress "PLANNED"}

        "collection-progress=PLANNED + include-non-operational=false - empty (explicit filter wins)"
        [] {:collection-progress "PLANNED" :include-non-operational "false"}))

    (side/eval-form `(cmr.search.config/set-enable-non-operational-collection-filter! false))))

(deftest search-collection-progress-identifier-bypass
  "Tests that collection identifiers bypass the non-operational collection filter (CMR-11240)."
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:ShortName "S1"
                                                                                :Version "V1"
                                                                                :EntryTitle "ET1"
                                                                                :CollectionProgress "ACTIVE"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:ShortName "S2"
                                                                                :Version "V2"
                                                                                :EntryTitle "ET2"
                                                                                :CollectionProgress "PLANNED"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:ShortName "S3"
                                                                                :Version "V3"
                                                                                :EntryTitle "ET3"
                                                                                :CollectionProgress "DEPRECATED"}))]

    (index/wait-until-indexed)

    (testing "flag OFF - all collections returned regardless of identifiers"
      (util/are3 [items search]
        (d/refs-match? items (search/find-refs :collection search))

        "no params"
        [coll1 coll2 coll3] {}

        "concept-id"
        [coll2] {:concept-id (:concept-id coll2)}

        "short-name + version"
        [coll2] {:short-name "S2" :version "V2"}))

    (side/eval-form `(cmr.search.config/set-enable-non-operational-collection-filter! true))

    (testing "flag ON - identifiers bypass filter, non-identifiers do not"
      (util/are3 [items search]
        (d/refs-match? items (search/find-refs :collection search))

        "no params - only ACTIVE returned"
        [coll1] {}

        "concept-id bypasses filter"
        [coll2] {:concept-id (:concept-id coll2)}

        "multiple concept-ids bypass filter"
        [coll1 coll2 coll3] {:concept-id [(:concept-id coll1) (:concept-id coll2) (:concept-id coll3)]}

        "entry-id bypasses filter"
        [coll2] {:entry-id "S2_V2"}

        "entry-title bypasses filter"
        [coll3] {:entry-title "ET3"}

        "short-name + version together bypass filter"
        [coll2] {:short-name "S2" :version "V2"}

        "native-id bypasses filter"
        [coll3] {:native-id "ET3"}

        "short-name alone does NOT bypass filter - only ACTIVE returned"
        [coll1] {:short-name "S1"}

        "version alone does NOT bypass filter - only ACTIVE returned"
        [coll1] {:version "V1"}))

    (side/eval-form `(cmr.search.config/set-enable-non-operational-collection-filter! false))))
