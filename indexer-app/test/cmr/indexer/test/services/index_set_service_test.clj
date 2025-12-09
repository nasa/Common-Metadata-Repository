(ns cmr.indexer.test.services.index-set-service-test
  "unit tests for index-set app service functions"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.elastic-utils.config :as es-config]
   [cmr.common.util :refer [are3]]
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
                                                        {:collection  {:all-collection-revisions "3_all_collection_revisions"
                                                                       :collections-v2 "3_collections_v2"}
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

  (testing "handles without leading numbers"
    (is (= "C2317035855-NSIDC_ECS"
           (#'svc/index-name->concept-id "c2317035855_nsidc_ecs"))))

  (testing "handles concept-id as index name"
    (is (= "C2317035855-NSIDC_ECS"
           (#'svc/index-name->concept-id "C2317035855-NSIDC_ECS"))))

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
    (let [index-set {:index-set {:concepts {:granule {:small_collections "1_small_collections"}}
                                 :granule {:rebalancing-collections ["C123-PROV" "C456-PROV"]}}}]
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
    (is (true? (#'svc/is-resharding-blocking-rebalancing? {:index-set {:concepts {:granule {:small_collections "1_small_collections"}}
                                                                       :collection {}
                                                                       :granule {:resharding-indexes #{"1_small_collections"}
                                                                                 :resharding-targets {"1_small_collections" "1_small_collections_100_shards"}}}}
                                                          "C123-PROV"))))

  (testing "returns true if the index for the concept-id is being resharded"
    (is (true? (#'svc/is-resharding-blocking-rebalancing? {:index-set {:concepts {:granule {:small_collections "1_small_collections"
                                                                                            (keyword "C123-PROV") "1_c123_prov"}}
                                                                       :collection {}
                                                                       :granule {:resharding-indexes #{"1_c123_prov"}
                                                                                 :resharding-targets {"1_c123_prov" "1_c123_prov_100_shards"}}}}
                                                          "C123-PROV"))))

  (testing "returns false if the index for the concept-id is not being resharded and neither is small_collections"
    (is (false? (#'svc/is-resharding-blocking-rebalancing? {:index-set {:concepts {:granule {:small_collections "1_small_collections"
                                                                                             (keyword "C123-PROV") "1_c123_prov"
                                                                                             (keyword "C124-PROV") "1_c124_prov"}}
                                                                        :collection {}
                                                                        :granule {:resharding-indexes #{"1_c123_prov"}
                                                                                  :resharding-targets {"1_c123_prov" "1_c123_prov_100_shards"}}}}
                                                           "C124-PROV")))))

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

  (testing "returns index config when found resharded index"
    (let [index-set {:index-set
                     {:collection
                      {:indexes [{:name "collection_index" :number_of_shards 3}]}}}]
      (is (= {:name "collection_index" :number_of_shards 3}
             (#'svc/get-index-config index-set :collection "collection_index_2_shards")))))

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
  (testing "returns concept type for regular index without shards"
    (let [index-set {:index-set
                     {:concepts {:granule {:small_collections "1_small_collections"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_small_collections")))))

  (testing "returns concept type for regular index with shards"
    (let [index-set {:index-set
                     {:concepts {:granule {:small_collections "1_small_collections_100_shards"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_small_collections_100_shards")))))

  (testing "returns concept type for concept ID style index"
    (let [index-set {:index-set
                     {:concepts {:granule {(keyword "C2317033465-NSIDC_ECS") "1_c2317033465_nsidc_ecs"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_c2317033465_nsidc_ecs")))))

  (testing "returns concept type for concept ID with shards"
    (let [index-set {:index-set
                     {:concepts {:granule {(keyword "C2317033465-NSIDC_ECS") "1_c2317033465_nsidc_ecs_5_shards"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_c2317033465_nsidc_ecs_5_shards")))))

  (testing "returns concept type for collection index with version"
    (let [index-set {:index-set
                     {:concepts {:collection {(keyword "collections-v2") "1_collections_v2"}}}}]
      (is (= :collection
             (svc/get-concept-type-for-index index-set "1_collections_v2")))))

  (testing "returns concept type for variable concept ID where provider has multiple underscores"
    (let [index-set {:index-set
                     {:concepts {:variable {(keyword "V123-NSIDC_ECS_FOO") "1_v123_nsidc_ecs_foo"}}}}]
      (is (= :variable
             (svc/get-concept-type-for-index index-set "1_v123_nsidc_ecs_foo")))))

  (testing "returns concept type for concept ID with single-part provider"
    (let [index-set {:index-set
                     {:concepts {:collection {(keyword "C2545314550-LPCLOUD") "1_c2545314550_lpcloud"}}}}]
      (is (= :collection
             (svc/get-concept-type-for-index index-set "1_c2545314550_lpcloud")))))

  (testing "returns concept type for concept ID with multi-part provider"
    (let [index-set {:index-set
                     {:concepts {:granule {(keyword "C2844842625-NSIDC_CPRD") "1_c2844842625_nsidc_cprd"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_c2844842625_nsidc_cprd")))))

  (testing "returns nil when index not found in any concept type"
    (let [index-set {:index-set
                     {:concepts {:granule {:small_collections "1_small_collections"}
                                 :collection {(keyword "collections-v2") "1_collections_v2"}}}}]
      (is (nil? (svc/get-concept-type-for-index index-set "1_nonexistent_index")))))

  (testing "returns nil when concepts map is empty"
    (let [index-set {:index-set {:concepts {}}}]
      (is (nil? (svc/get-concept-type-for-index index-set "1_some_index")))))

  (testing "returns nil when concepts key is missing"
    (let [index-set {:index-set {}}]
      (is (nil? (svc/get-concept-type-for-index index-set "1_some_index")))))

  (testing "returns nil when index-set is empty"
    (is (nil? (svc/get-concept-type-for-index {} "1_some_index"))))

  (testing "handles multiple concept types"
    (let [index-set {:index-set
                     {:concepts {:granule {:small_collections "1_small_collections"}
                                 :collection {(keyword "collections-v2") "1_collections_v2"}
                                 :tag {:tags "1_tags"}
                                 :variable {(keyword "V123-PROVIDER") "1_v123_provider"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_small_collections")))
      (is (= :collection
             (svc/get-concept-type-for-index index-set "1_collections_v2")))
      (is (= :tag
             (svc/get-concept-type-for-index index-set "1_tags")))
      (is (= :variable
             (svc/get-concept-type-for-index index-set "1_v123_provider")))))

  (testing "handles indexes with different leading numbers"
    (let [index-set {:index-set
                     {:concepts {:granule {:small_collections "2_small_collections"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "2_small_collections")))))

  (testing "handles multiple indexes per concept type"
    (let [index-set {:index-set
                     {:concepts {:granule {:small_collections "1_small_collections"
                                           (keyword "C123-PROV") "1_c123_prov"
                                           :large-collections "1_large_collections"}}}}]
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_small_collections")))
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_c123_prov")))
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_large_collections")))))

  (testing "canonical key matching is case-sensitive for concept IDs"
    (let [index-set {:index-set
                     {:concepts {:granule {(keyword "C2317033465-NSIDC_ECS") "1_c2317033465_nsidc_ecs"}}}}]
      ;; Should find it because get-canonical-key-name uppercases concept IDs
      (is (= :granule
             (svc/get-concept-type-for-index index-set "1_c2317033465_nsidc_ecs"))))))

(deftest test-get-reshard-status
  (let [index-set-id 1
        context nil
        small-collections-index "1_small_collections"
        valid-params {:elastic_name "gran-elastic"}
        empty-index-set {:index-set {}}]

    (testing "elastic name is not valid"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Empty elastic name is not allowed"
                            (svc/get-reshard-status context index-set-id small-collections-index {}))))
    (testing "concept-type is missing"
      (with-redefs [cmr.indexer.indexer-util/context->conn (fn [context elastic-name] nil)
                    cmr.indexer.common.index-set-util/get-index-set (fn [context elastic-name index-set-id] empty-index-set)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"The index \[.*\] does not exist\."
                              (svc/get-reshard-status context index-set-id small-collections-index valid-params)))))
    (testing "current target is missing because index is not being resharded"
      (with-redefs [cmr.indexer.indexer-util/context->conn (fn [context elastic-name] nil)
                    cmr.indexer.common.index-set-util/get-index-set (fn [context elastic-name index-set-id] empty-index-set)
                    cmr.indexer.services.index-set-service/get-concept-type-for-index (fn [index-set index] :granule)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"The index \[.*\] is not being resharded."
                              (svc/get-reshard-status context index-set-id small-collections-index valid-params)))))
    (testing "current status of index is nil because it's not being resharded"
      (with-redefs [cmr.indexer.indexer-util/context->conn (fn [context elastic-name] nil)
                    cmr.indexer.common.index-set-util/get-index-set (fn [context elastic-name index-set-id]
                                                                      {:index-set {:granule {:resharding-targets {(keyword small-collections-index) "1_small_collections_10_shards"}}}})
                    cmr.indexer.services.index-set-service/get-concept-type-for-index (fn [index-set index] :granule)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"The status of resharding index \[.*\] is not found."
                              (svc/get-reshard-status context index-set-id small-collections-index valid-params)))))
    (testing "index exists but status is complete already"
      (with-redefs [cmr.indexer.indexer-util/context->conn (fn [context elastic-name] nil)
                    cmr.indexer.common.index-set-util/get-index-set (fn [context elastic-name index-set-id]
                                                                      {:index-set {:granule {:resharding-targets {(keyword small-collections-index) "1_small_collections_10_shards"}
                                                                                             :resharding-status {(keyword small-collections-index) "COMPLETE"}}}})
                    cmr.indexer.services.index-set-service/get-concept-type-for-index (fn [index-set index] :granule)]
        (is (= {:original-index small-collections-index
                :reshard-index "1_small_collections_10_shards"
                :reshard-status "COMPLETE"}
               (svc/get-reshard-status context index-set-id small-collections-index valid-params)))))
      (testing "index exists and status is not complete and reindexing is still in progress, then current status will be returned as is."
        (with-redefs [cmr.indexer.indexer-util/context->conn (fn [context elastic-name] nil)
                      cmr.indexer.common.index-set-util/get-index-set (fn [context elastic-name index-set-id]
                                                                        {:index-set {:granule {:resharding-targets {(keyword small-collections-index) "1_small_collections_10_shards"}
                                                                                               :resharding-status {(keyword small-collections-index) "IN_PROGRESS"}}}})
                      cmr.indexer.services.index-set-service/get-concept-type-for-index (fn [index-set index] :granule)
                      cmr.elastic-utils.es-helper/reindexing-still-in-progress? (fn [conn index] true)]
          (is (= {:original-index small-collections-index
                  :reshard-index "1_small_collections_10_shards"
                  :reshard-status "IN_PROGRESS"}
                 (svc/get-reshard-status context index-set-id small-collections-index valid-params)))))
    (testing "when index exists and status is not complete and reindexing is done, then status will be updated to COMPLETE and index set will updated.
    Correct status resp will be returned."
      (let [mock-calls (atom 0)
            get-index-set-mock-fn (fn [& args]
                        (swap! mock-calls inc)
                        (case @mock-calls
                          1 {:index-set {:granule {:resharding-targets {(keyword small-collections-index) "1_small_collections_10_shards"}
                                                   :resharding-status {(keyword small-collections-index) "IN_PROGRESS"}}}}
                          2 {:index-set {:granule {:resharding-targets {(keyword small-collections-index) "1_small_collections_10_shards"}
                                                   :resharding-status {(keyword small-collections-index) "COMPLETE"}}}}))]
        (with-redefs [cmr.indexer.indexer-util/context->conn (fn [context elastic-name] nil)
                      cmr.indexer.common.index-set-util/get-index-set get-index-set-mock-fn
                      cmr.indexer.services.index-set-service/get-concept-type-for-index (fn [index-set index] :granule)
                      cmr.elastic-utils.es-helper/reindexing-still-in-progress? (fn [conn index] false)
                      cmr.indexer.services.index-set-service/update-resharding-status (fn [context index-set-id index status elastic-name] nil)]
          (is (= {:original-index small-collections-index
                  :reshard-index "1_small_collections_10_shards"
                  :reshard-status "COMPLETE"}
                 (svc/get-reshard-status context index-set-id small-collections-index valid-params))))
        ))))
