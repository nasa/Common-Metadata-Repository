(ns cmr.indexer.test.services.index-set-service-test
  "unit tests for index-set app service functions"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.elastic-utils.config :as es-config]
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
  (let [expected-pruned-gran-index-set {:id 3
                                        :name "cmr-base-index-set"
                                        :concepts (merge
                                                    {:granule {:small_collections "3_small_collections"
                                                               :C4-PROV3 "3_c4_prov3"
                                                               :C5-PROV5 "3_c5_prov5"}
                                                     :deleted-granule {}})}
        expected-pruned-non-gran-index-set {:id 3
                                            :name "cmr-base-index-set"
                                            :concepts (merge
                                                        {:collection  {:C6-PROV3 "3_c6_prov3"
                                                                       :C4-PROV2 "3_c4_prov2"}
                                                         :tag {}
                                                         :variable {}
                                                         :service {}
                                                         :tool {}
                                                         :autocomplete {}
                                                         :subscription {}}
                                                        (zipmap (keys (index-set-gen/generic-mappings-generator)) (repeat {})))}
        actual-pruned-non-gran-index-set (svc/prune-index-set
                                           (:index-set util/sample-index-set) es-config/elastic-name)
        actual-pruned-gran-index-set (svc/prune-index-set
                                       (:index-set util/sample-index-set) es-config/gran-elastic-name)]
    (is (= expected-pruned-gran-index-set actual-pruned-gran-index-set))
    (is (= expected-pruned-non-gran-index-set actual-pruned-non-gran-index-set))))

(deftest split-index-set-by-cluster-test
  (let [file-path (str
                    (-> (clojure.java.io/file ".") .getAbsolutePath)
                    "/test/cmr/indexer/test/services/test_files/combined-index-set.json")
        combined-index-set-map (-> file-path
                                   slurp
                                   (json/parse-string true))
        split-index-set-map (svc/split-index-set-by-cluster combined-index-set-map)

        actual-gran-index-set (get split-index-set-map (keyword es-config/gran-elastic-name))
        actual-non-gran-index-set (get split-index-set-map (keyword es-config/elastic-name))

        expected-gran-index-set-file-path (str
                                            (-> (clojure.java.io/file ".") .getAbsolutePath)
                                            "/test/cmr/indexer/test/services/test_files/expected-gran-index-set.json")
        expected-gran-index-set (-> expected-gran-index-set-file-path
                                    slurp
                                    (json/parse-string true))
        expected-non-gran-index-set-file-path (str
                                                (-> (clojure.java.io/file ".") .getAbsolutePath)
                                                "/test/cmr/indexer/test/services/test_files/expected-non-gran-index-set.json")
        expected-non-gran-index-set (-> expected-non-gran-index-set-file-path
                                        slurp
                                        (json/parse-string true))]
    (is (= actual-gran-index-set expected-gran-index-set))
    (is (= actual-non-gran-index-set expected-non-gran-index-set))))


(deftest add-searchable-generic-types-test
  (let [;; setup non-gran list
        initial-non-gran-concept-list [:autocomplete
                                       :collection
                                       :service
                                       :subscription
                                       :tag
                                       :tool
                                       :variable]
        updated-non-gran-list (svc/add-searchable-generic-types initial-non-gran-concept-list es-config/elastic-name)
        expected-non-gran-list [:autocomplete :collection :service :subscription :tag :tool
                                :variable :generic-order-option-draft :generic-grid-draft
                                :generic-variable-draft :generic-grid :generic-data-quality-summary-draft
                                :generic-citation :generic-visualization-draft :generic-tool-draft
                                :generic-order-option :generic-visualization :generic-data-quality-summary
                                :generic-citation-draft :generic-collection-draft :generic-service-draft]

        ;; setup gran list
        initial-gran-concept-list [:deleted-granule :granule]
        updated-gran-list (svc/add-searchable-generic-types initial-gran-concept-list es-config/gran-elastic-name)
        expected-gran-list [:deleted-granule :granule]]

    (is (= updated-non-gran-list expected-non-gran-list))
    (is (= updated-gran-list expected-gran-list))))

(deftest test-index-name->concept-id
  (testing "converts basic granule index name to concept ID"
    (is (= "C2317035855-NSIDC_ECS"
           (#'svc/index-name->concept-id "1_c2317035855_nsidc_ecs"))))

  (testing "converts granule index name with shards suffix to concept ID"
    (is (= "C2317035855-NSIDC_ECS"
           (#'svc/index-name->concept-id "1_c2317035855_nsidc_ecs_5_shards"))))

  (testing "converts index name with single-part provider"
    (is (= "C2545314550-LPCLOUD"
           (#'svc/index-name->concept-id "1_c2545314550_lpcloud"))))

  (testing "converts index name with multi-part provider"
    (is (= "C2844842625-NSIDC_CPRD"
           (#'svc/index-name->concept-id "1_c2844842625_nsidc_cprd"))))

  (testing "handles different shard counts"
    (is (= "C2317035855-NSIDC_ECS"
           (#'svc/index-name->concept-id "1_c2317035855_nsidc_ecs_10_shards"))))

  (testing "handles different leading numbers"
    (is (= "C2317035855-NSIDC_ECS"
           (#'svc/index-name->concept-id "2_c2317035855_nsidc_ecs"))))

  (testing "returns nil for nil input"
    (is (nil? (#'svc/index-name->concept-id nil))))

  (testing "returns nil for empty string"
    (is (nil? (#'svc/index-name->concept-id "")))))

(deftest test-is-rebalancing?
  (testing "returns false when rebalancing-collections is empty"
    (let [index-set {:index-set {:granule {:rebalancing-collections []}}}]
      (is (false? (#'svc/is-rebalancing? index-set "1_small_collections")))))

  (testing "returns false when no rebalancing is happening"
    (let [index-set {:index-set {:granule {}}}]
      (is (false? (#'svc/is-rebalancing? index-set "1_c123_prov")))))

  (testing "returns false when rebalancing-collections does not include the collection for the index"
    (let [index-set {:index-set {:granule {:rebalancing-collections ["C123-PROV"]}}}]
      (is (false? (#'svc/is-rebalancing? index-set "1_c124_prov")))))

  (testing "returns true when rebalancing-collections includes the collection for the index"
    (let [index-set {:index-set {:granule {:rebalancing-collections ["C123-PROV" "C456-PROV"]}}}]
      (is (true? (#'svc/is-rebalancing? index-set "1_c123_prov")))))

  (testing "returns true when rebalancing is happening and the index is small-collections"
    (let [index-set {:index-set {:concepts {:granule {"small_collections" "1_small_collections"}} :granule {:rebalancing-collections ["C123-PROV" "C456-PROV"]}}}]
      (is (true? (#'svc/is-rebalancing? index-set "1_small_collections"))))))

(deftest test-is-resharding?
  (testing "returns false when no resharding-indexes present"
    (is (false? (#'svc/is-resharding? {:index-set {:granule {}
                                                   :collection {}}}
                                      "1_small_collections"))))

  (testing "returns true when index is resharding"
    (is (true? (#'svc/is-resharding? {:index-set {:collection {}
                                                  :granule {:resharding-indexes #{"1_c123_prov"}
                                                            :resharding-targets {"1_c123_prov" "1_c123_prov_5_shards"}}}}
                                     "1_c123_prov"))))

  (testing "returns true when index is a resharding target"
    (is (true? (#'svc/is-resharding? {:index-set {:collection {}
                                                  :granule {:resharding-indexes #{"1_c123_prov"}
                                                            :resharding-targets {"1_c123_prov" "1_c123_prov_5_shards"}}}}
                                     "1_c123_prov_5_shards")))))

(deftest is-resharding-blocking-rebalancing?
  (testing "returns true if small_collections is being resharded"
    (is (true? (#'svc/is-resharding-blocking-rebalancing? {:index-set {:concepts {:granule {"small_collections" "1_small_collections"}}
                                                                       :collection {}
                                                                       :granule {:resharding-indexes #{"1_small_collections"}
                                                                                 :resharding-targets {"1_small_collections" "1_small_collections_100_shards"}}}}
                                                          "C123_PROV"))))

  (testing "returns true if the index for the concept-id is being resharded"
    (is (true? (#'svc/is-resharding-blocking-rebalancing? {:index-set {:concepts {:granule {"small_collections" "1_small_collections"
                                                                                            "C123_PROV" "1_c123_prov"}}
                                                                       :collection {}
                                                                       :granule {:resharding-indexes #{"1_c123_prov"}
                                                                                 :resharding-targets {"1_c123_prov" "1_c123_prov_100_shards"}}}}
                                                          "C123_PROV"))))

  (testing "returns false if the index for the concept-id is not being resharded and neither is small_collections"
    (is (false? (#'svc/is-resharding-blocking-rebalancing? {:index-set {:concepts {:granule {"small_collections" "1_small_collections"
                                                                                             "C123_PROV" "1_c123_prov"
                                                                                             "C124_PROV" "1_c124_prov"}}
                                                                        :collection {}
                                                                        :granule {:resharding-indexes #{"1_c123_prov"}
                                                                                  :resharding-targets {"1_c123_prov" "1_c123_prov_100_shards"}}}}
                                                           "C124_PROV")))))

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
