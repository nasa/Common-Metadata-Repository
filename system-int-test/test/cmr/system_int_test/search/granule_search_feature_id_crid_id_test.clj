(ns cmr.system-int-test.search.granule-search-feature-id-crid-id-test
  "Search CMR granules by feature ids and crid ids"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-feature-id-crid-id
  (let [coll1 (d/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection {:EntryTitle "ET1" :ShortName "S1"}))
        coll2 (d/ingest-umm-spec-collection
               "PROV2" (data-umm-c/collection {:EntryTitle "ET2" :ShortName "S2"}))
        coll1-concept-id (:concept-id coll1)
        coll2-concept-id (:concept-id coll2)
        gran1 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                         coll1 coll1-concept-id {:day-night "DAY"
                                                 :feature-ids ["feature1"]
                                                 :crid-ids ["crid1"]})
                        {:format :umm-json})
        gran2 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                         coll1 coll1-concept-id {:day-night "DAY"
                                                 :feature-ids ["FEATURE2"]
                                                 :crid-ids ["CRID2"]})
                        {:format :umm-json})
        gran3 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                         coll1 coll1-concept-id {:day-night "DAY"
                                                 :feature-ids ["super_power"]
                                                 :crid-ids ["super_crid"]})
                        {:format :umm-json})
        gran4 (d/ingest "PROV2"
                        (dg/granule-with-umm-spec-collection
                         coll2 coll2-concept-id {:day-night "DAY"
                                                 :feature-ids ["feature1" "feature2"]
                                                 :crid-ids ["crid1" "crid2"]})
                        {:format :umm-json})
        gran5 (d/ingest "PROV2"
                        (dg/granule-with-umm-spec-collection
                         coll2 coll2-concept-id {:day-night "DAY"
                                                 :feature-ids ["feature123"]
                                                 :crid-ids ["crid123"]})
                        {:format :umm-json})
        gran6 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-concept-id))]
    (index/wait-until-indexed)

    (testing "search by feature-id"
      (are [items feature-ids options]
        (let [params (merge {:feature-id feature-ids}
                            (when options
                              {"options[feature-id]" options}))]
          (d/refs-match? items (search/find-refs :granule params)))

        [] ["FAKE"] {}
        [gran5] ["feature123"] {}
        [gran1 gran4] ["feature1"] {}
        [gran2 gran4] ["feature2"] {}
        [gran1 gran2 gran4] ["feature1" "feature2"] {}

        ;; ignore case
        [gran2 gran4] ["FEATURE2"] {}
        [gran2 gran4] ["FEATURE2"] {:ignore-case true}
        [gran2] ["FEATURE2"] {:ignore-case false}

        ;; pattern
        [gran1 gran2 gran4 gran5] "feature*" {:pattern true}
        [gran1 gran2 gran4] "feature?" {:pattern true}

        ;; and option
        [gran4] ["feature1" "feature2"] {:and true}))

    (testing "search by crid-id"
      (are [items crid-ids options]
        (let [params (merge {:crid-id crid-ids}
                            (when options
                              {"options[crid-id]" options}))]
          (d/refs-match? items (search/find-refs :granule params)))

        [] ["FAKE"] {}
        [gran5] ["crid123"] {}
        [gran1 gran4] ["crid1"] {}
        [gran2 gran4] ["crid2"] {}
        [gran1 gran2 gran4] ["crid1" "crid2"] {}

        ;; ignore case
        [gran2 gran4] ["CRID2"] {}
        [gran2 gran4] ["CRID2"] {:ignore-case true}
        [gran2] ["CRID2"] {:ignore-case false}

        ;; pattern
        [gran1 gran2 gran4 gran5] "crid*" {:pattern true}
        [gran1 gran2 gran4] "crid?" {:pattern true}

        ;; and option
        [gran4] ["crid1" "crid2"] {:and true}))))
