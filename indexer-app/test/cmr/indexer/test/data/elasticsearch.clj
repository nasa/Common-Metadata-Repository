(ns cmr.indexer.test.data.elasticsearch
  (:require [clojure.test :refer :all]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.indexer.data.index-set :as i]))

(def test-index-set
  "A real copy of an index set from UAT with the mappings replaced to be smaller and reduce churn"
  {:index-set
   {:name "cmr-base-index-set",
    :id 1,
    :create-reason "indexer app requires this index set",
    :collection {:indexes [{:name "collections-v2",
                            :settings i/collection-setting-v2}
                           {:name "all-collection-revisions",
                            :settings i/collection-setting-v1},]
                 :mapping i/collection-mapping},
    :granule {:indexes
              [{:name "small_collections",
                :settings i/granule-settings-for-small-collections-index}
               {:name "C274209-USGS_EROS",
                :settings i/granule-settings-for-individual-indexes}
               {:name "C274211-USGS_EROS",
                :settings i/granule-settings-for-individual-indexes}],
              :individual-index-settings i/granule-settings-for-individual-indexes
              :mapping i/granule-mapping},
    :tag {:indexes [{:name "tags",
                     :settings i/tag-setting}],
          :mapping i/tag-mapping},
    :concepts {:collection {:collections "1_collections_v2",
                            :all-collection-revisions "1_all_collection_revisions"},
               :granule {:small_collections "1_small_collections",
                         :C274209-USGS_EROS "1_c274209_usgs_eros",
                         :C274211-USGS_EROS "1_c274211_usgs_eros"},
               :tag {:tags "1_tags"}}}})



(deftest extra-granule-indexes-test
  (testing "extra indexes configured"
    (is (= ["C274209-USGS_EROS" "C274211-USGS_EROS"]
           (i/index-set->extra-granule-indexes test-index-set))))
  (testing "no extra indexes configured"
    (is (empty? (i/index-set->extra-granule-indexes (i/index-set nil)))))
  (testing "Nil index set"
    ;; A nil index set is possible if there is no existing index set.
    (is (empty? (i/index-set->extra-granule-indexes nil)))))


(deftest requires-update-test
  (testing "No updates required"
    (is (not (es/requires-update? test-index-set
                                  (i/index-set (i/index-set->extra-granule-indexes test-index-set))))))
  (testing "Updates required from individual index settings"
    (is (es/requires-update? (update-in test-index-set [:index-set :granule] dissoc :individual-index-settings)
                             (i/index-set (i/index-set->extra-granule-indexes test-index-set))))))
