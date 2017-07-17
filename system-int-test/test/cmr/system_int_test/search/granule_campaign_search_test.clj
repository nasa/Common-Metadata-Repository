(ns cmr.system-int-test.search.granule-campaign-search-test
  "Integration test for CMR granule temporal search"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-campaign
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Projects (data-umm-cmn/projects "ABC" "XYZ" "PDQ" "RST")}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                                   :project-refs ["ABC"]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule2"
                                                   :project-refs ["ABC" "XYZ"]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule3"
                                                       :project-refs ["PDQ" "RST"]}))]
    (index/wait-until-indexed)

    (testing "search by campaign"
      (are [campaign-sn items] (d/refs-match? items (search/find-refs :granule {:campaign campaign-sn}))
           "XYZ" [gran2]
           "ABC" [gran1 gran2]))
    (testing "search by project"
      (are [project-sn items] (d/refs-match? items (search/find-refs :granule {:project project-sn}))
           "XYZ" [gran2]
           "ABC" [gran1 gran2]))
    (testing "search by multiple campaigns"
      (are [campaign-kvs items] (d/refs-match? items (search/find-refs :granule campaign-kvs))
           ;; tests with AND
           {"campaign[]" ["XYZ" "PDQ"], "options[campaign][and]" "false"} [gran2 gran3]
           {"campaign[]" ["XYZ" "ABC"], "options[campaign][and]" "true"} [gran2]
           ;; OR is the default
           {"campaign[]" ["XYZ" "ABC"]} [gran1 gran2]
           ;; some missing campaigns
           {"campaign[]" ["ABC" "LMN"]} [gran1 gran2]
           ;; not found
           {"campaign[]" ["LMN"]} []))

    (testing "search by campaign with aql"
      (are [items campaigns options]
           (let [condition (merge {:CampaignShortName campaigns} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran2] "XYZ" {}
           [gran1 gran2] "ABC" {}
           [gran2 gran3] ["XYZ" "PDQ"] {}
           [gran1 gran2] ["ABC" "LMN"] {}
           [] "LMN" {}
           [] "abc" {}
           [gran1 gran2] "abc" {:ignore-case true}
           [] "abc" {:ignore-case false}))))
