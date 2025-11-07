(ns cmr.indexer.test.data.index-set
  "unit tests for index-set functions"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.indexer.data.index-set :as index-set]))

(deftest get-canonical-key-name-test
  (are3 [expected index-name]
        (is (= expected (index-set/get-canonical-key-name index-name)))

        "small_collections keep underscore"
        "small_collections"
        "1_small_collections"

        "reshared small_collections"
        "small_collections"
        "1_small_collections_20_shards"

        "deleted_granules keep underscore"
        "deleted_granules"
        "1_deleted_granules"

        "reshared deleted_granules"
        "deleted_granules"
        "1_deleted_granules_2_shards"

        "individual granule index"
        "C2317033465-NSIDC_ECS"
        "1_c2317033465_nsidc_ecs"

        "resharded individual granule index"
        "C2317033465-NSIDC_ECS"
        "1_c2317033465_nsidc_ecs_8_shards"

        "collections index"
        "collections-v2"
        "1_collections_v2"

        "reshareded collections index"
        "collections-v2"
        "1_collections_v2_2_shards"

        "generic concept index"
        "generic-citation-draft"
        "1_generic_citation_draft"

        "resharded generic concept index"
        "generic-citation-draft"
        "1_generic_citation_draft_1_shards"

        "generic concept all revisions index"
        "all-generic-service-draft-revisions"
        "1_all_generic_service_draft_revisions"

        "resharded generic concept all revisions index"
        "all-generic-service-draft-revisions"
        "1_all_generic_service_draft_revisions_1_shards"))
