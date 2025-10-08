(ns cmr.indexer.test.services.index-set-service-test
  "unit tests for index-set app service functions"
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.data.index-set-generics :as index-set-gen]
   [cmr.indexer.services.index-set-service :as svc]
   [cmr.indexer.test.utility :as util]))

(deftest gen-valid-index-name-test
  (let [index-set-id "77"
        req-index-name1 "C4-collections"
        req-index-name2 "C4-Prov3"
        req-index-name3 "C5_prov5"
        expected-index-name1 "77_c4_collections"
        expected-index-name2 "77_c4_prov3"
        expected-index-name3 "77_c5_prov5"
        actual-index-name1 (svc/gen-valid-index-name index-set-id req-index-name1)
        actual-index-name2 (svc/gen-valid-index-name index-set-id req-index-name2)
        actual-index-name3 (svc/gen-valid-index-name index-set-id req-index-name3)]
    (is (= expected-index-name1 actual-index-name1))
    (is (= expected-index-name2 actual-index-name2))
    (is (= expected-index-name3 actual-index-name3))))

(deftest prune-index-set-test
  (let [pruned-index-set {:id 3
                          :name "cmr-base-index-set"
                          :concepts (merge
                                     {:collection  {:C6-PROV3 "3_c6_prov3"
                                                    :C4-PROV2 "3_c4_prov2"}
                                      :granule {:small_collections "3_small_collections"
                                                :C4-PROV3 "3_c4_prov3"
                                                :C5-PROV5 "3_c5_prov5"}
                                      :tag {}
                                      :variable {}
                                      :service {}
                                      :tool {}
                                      :deleted-granule {}
                                      :autocomplete {}
                                      :subscription {}}
                                     (zipmap (keys (index-set-gen/generic-mappings-generator)) (repeat {})))}]
    (is (= pruned-index-set (svc/prune-index-set (:index-set util/sample-index-set))))))

(deftest test-is-rebalancing?
  (testing "returns false when rebalancing-collections is empty"
    (let [index-set {:index-set {:granule {:rebalancing-collections []}}}]
      (is (false? (#'svc/is-rebalancing? index-set)))))

  (testing "returns false when rebalancing-collections has one collection"
    (let [index-set {:index-set {:granule {:rebalancing-collections ["C123-PROV"]}}}]
      (is (false? (#'svc/is-rebalancing? index-set)))))

  (testing "returns false when rebalancing-collections has multiple collections"
    (let [index-set {:index-set {:granule {:rebalancing-collections ["C123-PROV" "C456-PROV"]}}}]
      (is (false? (#'svc/is-rebalancing? index-set)))))

  (testing "returns false when rebalancing-collections key is missing"
    (let [index-set {:index-set {:granule {}}}]
      (is (false? (#'svc/is-rebalancing? index-set))))))

(deftest test-is-resharding?
  (testing "returns false when no resharding-indexes present"
    (is (false? (#'svc/is-resharding? {:index-set {:granule {}
                                                   :collection {}}}))))

  (testing "returns true when collection has resharding-indexes"
    (is (true? (#'svc/is-resharding? {:index-set {:granule {}
                                                  :collection {:resharding-indexes []}}}))))

  (testing "returns true when granule has resharding-indexes"
    (is (true? (#'svc/is-resharding? {:index-set {:granule {:resharding-indexes []}
                                                  :collection {}}}))))

  (testing "returns true when multiple indexes have resharding-indexes"
    (is (true? (#'svc/is-resharding? {:index-set {:granule {:resharding-indexes []}
                                                  :collection {:resharding-indexes []}}})))))

(deftest test-get-resharded-index-name
  (testing "appends shard count to index name without existing shard count"
    (is (= "my_index_5_shards"
           (svc/get-resharded-index-name "my_index" 5))))

  (testing "replaces existing shard count with new shard count"
    (is (= "my_index_10_shards"
           (svc/get-resharded-index-name "my_index_3_shards" 10))))

  (testing "handles index names with underscores"
    (is (= "my_complex_index_name_8_shards"
           (svc/get-resharded-index-name "my_complex_index_name" 8))))

  (testing "replaces shard count in complex index name"
    (is (= "my_complex_index_name_12_shards"
           (svc/get-resharded-index-name "my_complex_index_name_4_shards" 12))))

  (testing "handles single digit shard counts"
    (is (= "index_1_shards"
           (svc/get-resharded-index-name "index_99_shards" 1))))

  (testing "handles large shard counts"
    (is (= "index_1000_shards"
           (svc/get-resharded-index-name "index_5_shards" 1000))))

  (testing "does not replace numbers that are not shard counts"
    (is (= "index_v2_10_shards"
           (svc/get-resharded-index-name "index_v2" 10))))

  (testing "does not replace numbers followed by other text"
    (is (= "index_5_shards_backup_3_shards"
           (svc/get-resharded-index-name "index_5_shards_backup" 3)))))

(deftest test-add-resharding-index
  (testing "adds index to existing vector of resharding indexes"
    (is (= ["index1" "index2"]
           (#'svc/add-resharding-index ["index1"] "index2"))))

  (testing "creates new set with index when resharding-indexes is nil"
    (is (= #{"index1"}
           (#'svc/add-resharding-index nil "index1"))))

  (testing "creates new set with index when resharding-indexes is false"
    (is (= #{"index1"}
           (#'svc/add-resharding-index false "index1"))))

  (testing "adds index to empty collection"
    (is (= ["new-index"]
           (#'svc/add-resharding-index [] "new-index"))))

  (testing "successfully adds when index name is similar but not identical"
    (is (= ["index1" "index11"]
           (#'svc/add-resharding-index ["index1"] "index11"))))

  (testing "throws error when index already exists in collection"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The index set already contains resharding index \[index1\]"
         (#'svc/add-resharding-index ["index1" "index2"] "index1")))))

(deftest test-get-index-config
  (testing "returns index config when found for granule concept type"
    (let [index-set {:index-set
                     {:granule
                      {:indexes [{:name "granule_index_1" :number_of_shards 5}
                                 {:name "granule_index_2" :number_of_shards 10}]}}}]
      (is (= {:name "granule_index_1" :number_of_shards 5}
             (#'svc/get-index-config index-set :granule "granule_index_1")))))

  (testing "returns index config when found for collection concept type"
    (let [index-set {:index-set
                     {:collection
                      {:indexes [{:name "collection_index" :number_of_shards 3}]}}}]
      (is (= {:name "collection_index" :number_of_shards 3}
             (#'svc/get-index-config index-set :collection "collection_index")))))

  (testing "returns nil when index name not found"
    (let [index-set {:index-set
                     {:granule
                      {:indexes [{:name "granule_index_1" :number_of_shards 5}]}}}]
      (is (nil? (#'svc/get-index-config index-set :granule "nonexistent_index")))))

  (testing "returns nil when concept type not found"
    (let [index-set {:index-set
                     {:granule
                      {:indexes [{:name "granule_index_1" :number_of_shards 5}]}}}]
      (is (nil? (#'svc/get-index-config index-set :collection "granule_index_1"))))))

(deftest test-get-concept-type-for-index
  (testing "returns concept type when index found for granule"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "1_cmr_granules_v1"
                                           "granule-v2" "1_cmr_granules_v2"}
                                 :collection {"collection-v1" "1_cmr_collections_v1"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_cmr_granules_v1")))))

  (testing "returns concept type when index found for collection"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "1_cmr_granules_v1"}
                                 :collection {"collection-v1" "1_cmr_collections_v1"
                                              "collection-v2" "1_cmr_collections_v2"}}}}]
      (is (= :collection
             (svc/get-concept-type-for-index index-set "1_cmr_collections_v2")))))

  (testing "returns nil when index not found in any concept type"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "1_cmr_granules_v1"}
                                 :collection {"collection-v1" "1_cmr_collections_v1"}}}}]
      (is (nil? (svc/get-concept-type-for-index index-set "nonexistent_index")))))

  (testing "handles multiple indexes per concept type"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "1_cmr_granules_v1"
                                           "granule-v2" "1_cmr_granules_v2"
                                           "granule-v3" "1_cmr_granules_v3"}
                                 :collection {"collection-v1" "1_cmr_collections_v1"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_cmr_granules_v2")))))

  (testing "handles additional concept types"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "1_cmr_granules_v1"}
                                 :collection {"collection-v1" "1_cmr_collections_v1"}
                                 :tag {"tag-v1" "1_cmr_tags_v1"}
                                 :variable {"var-v1" "1_cmr_variables_v1"}}}}]
      (is (= :tag
             (svc/get-concept-type-for-index index-set "1_cmr_tags_v1")))
      (is (= :variable
             (svc/get-concept-type-for-index index-set "1_cmr_variables_v1")))))

  (testing "does not match on partial index names"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "1_cmr_granules_v1"}}}}]
      (is (nil? (svc/get-concept-type-for-index index-set "1_cmr_granules")))))

  (testing "exact string matching for index names"
    (let [index-set {:index-set
                     {:concepts {:granule {"granule-v1" "index_5_shards"}
                                 :collection {"collection-v1" "index_10_shards"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "index_5_shards")))
      (is (= :collection
             (svc/get-concept-type-for-index index-set "index_10_shards"))))))

(deftest test-get-resharding-index-target
  (testing "returns target index when resharding target exists for granule"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:granule_index_5_shards "granule_index_10_shards"}}}}]
      (is (= "granule_index_10_shards"
             (#'index-set/get-resharding-index-target index-set :granule "granule_index_5_shards")))))

  (testing "returns target index when resharding target exists for collection"
    (let [index-set {:index-set
                     {:collection
                      {:resharding-targets {:collection_index_3_shards "collection_index_6_shards"}}}}]
      (is (= "collection_index_6_shards"
             (#'index-set/get-resharding-index-target index-set :collection "collection_index_3_shards")))))

  (testing "returns nil when index is not being resharded"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:granule_index_5_shards "granule_index_10_shards"}}}}]
      (is (nil? (#'index-set/get-resharding-index-target index-set :granule "other_index")))))

  (testing "returns nil when wrong concept type is queried"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:granule_index_5_shards "granule_index_10_shards"}}}}]
      (is (nil? (#'index-set/get-resharding-index-target index-set :collection "granule_index_5_shards")))))

  (testing "returns nil when resharding-targets key is missing"
    (let [index-set {:index-set {:granule {}}}]
      (is (nil? (#'index-set/get-resharding-index-target index-set :granule "some_index")))))

  (testing "returns nil when concept type key is missing"
    (let [index-set {:index-set {}}]
      (is (nil? (#'index-set/get-resharding-index-target index-set :granule "some_index")))))

  (testing "returns nil when index-set is empty"
    (is (nil? (#'index-set/get-resharding-index-target {} :granule "some_index"))))

  (testing "returns nil when index-set is nil"
    (is (nil? (#'index-set/get-resharding-index-target nil :granule "some_index"))))

  (testing "handles multiple resharding targets for same concept type"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:index_a_5_shards "index_a_10_shards"
                                            :index_b_3_shards "index_b_6_shards"}}}}]
      (is (= "index_a_10_shards"
             (#'index-set/get-resharding-index-target index-set :granule "index_a_5_shards")))
      (is (= "index_b_6_shards"
             (#'index-set/get-resharding-index-target index-set :granule "index_b_3_shards")))))

  (testing "handles resharding targets across different concept types"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:granule_index "granule_target"}}
                      :collection
                      {:resharding-targets {:collection_index "collection_target"}}}}]
      (is (= "granule_target"
             (#'index-set/get-resharding-index-target index-set :granule "granule_index")))
      (is (= "collection_target"
             (#'index-set/get-resharding-index-target index-set :collection "collection_index")))))

  (testing "converts string index to keyword for lookup"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:my_index "target_index"}}}}]
      ;; The function converts the string "my_index" to keyword :my_index
      (is (= "target_index"
             (#'index-set/get-resharding-index-target index-set :granule "my_index")))))

  (testing "handles index names with special characters"
    (let [index-set {:index-set
                     {:granule
                      {:resharding-targets {:index-with-dashes_10_shards "new_index"}}}}]
      (is (= "new_index"
             (#'index-set/get-resharding-index-target index-set :granule "index-with-dashes_10_shards")))))

  (testing "returns nil for empty resharding-targets map"
    (let [index-set {:index-set {:granule {:resharding-targets {}}}}]
      (is (nil? (#'index-set/get-resharding-index-target index-set :granule "some_index"))))))
