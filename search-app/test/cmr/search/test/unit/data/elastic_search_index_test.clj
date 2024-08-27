(ns cmr.search.test.unit.data.elastic-search-index-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-index-name-cache :as idx-names-cache]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.search.data.elastic-search-index :as search-index]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(def cached-index-names
  {:generic-order-option {:generic-order-option "1_generic_order_option" :all-generic-order-option-revisions "1_all_generic_order_option_revisions"}
   :service {:services "1_services" :all-service-revisions "1_all_service_revisions"}
   :generic-tool-draft {:generic-tool-draft "1_generic_tool_draft" :all-generic-tool-draft-revisions "1_all_generic_tool_draft_revisions"}
   :variable {:variables "1_variables" :all-variable-revisions "1_all_variable_revisions"}
   :generic-grid-draft {:generic-grid-draft "1_generic_grid_draft" :all-generic-grid-draft-revisions "1_all_generic_grid_draft_revisions"}
   :generic-service-draft {:generic-service-draft "1_generic_service_draft" :all-generic-service-draft-revisions "1_all_generic_service_draft_revisions"}
   :deleted-granule {:deleted_granules "1_deleted_granules"}
   :tool {:tools "1_tools" :all-tool-revisions "1_all_tool_revisions"}
   :generic-collection-draft {:generic-collection-draft "1_generic_collection_draft" :all-generic-collection-draft-revisions "1_all_generic_collection_draft_revisions"}
   :granule {:small_collections "1_small_collections"}
   :generic-order-option-draft {:generic-order-option-draft "1_generic_order_option_draft" :all-generic-order-option-draft-revisions "1_all_generic_order_option_draft_revisions"}
   :generic-data-quality-summary-draft {:generic-data-quality-summary-draft "1_generic_data_quality_summary_draft" :all-generic-data-quality-summary-draft-revisions "1_all_generic_data_quality_summary_draft_revisions"}
   :generic-variable-draft {:generic-variable-draft "1_generic_variable_draft" :all-generic-variable-draft-revisions "1_all_generic_variable_draft_revisions"}
   :autocomplete {:autocomplete "1_autocomplete"}
   :tag {:tags "1_tags"}
   :generic-grid {:generic-grid "1_generic_grid" :all-generic-grid-revisions "1_all_generic_grid_revisions"}
   :generic-data-quality-summary {:generic-data-quality-summary "1_generic_data_quality_summary" :all-generic-data-quality-summary-revisions "1_all_generic_data_quality_summary_revisions"}
   :generic-visualization {:generic-visualization "1_generic_visualization" :all-generic-visualization-revisions "1_all_generic_visualization_revisions"}
   :collection {:collections-v2 "1_collections_v2" :all-collection-revisions "1_all_collection_revisions"}
   :subscription {:subscriptions "1_subscriptions" :all-subscription-revisions "1_all_subscription_revisions"}
   :rebalancing-collections ()})

(deftest get-cached-index-names-test
  (let [cache-key idx-names-cache/index-names-cache-key
        cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]
                                                         :read-connection (redis-config/redis-read-conn-opts)
                                                         :primary-connection (redis-config/redis-conn-opts)})
        _ (hash-cache/reset cache cache-key)
        context {:system {:caches {cache-key cache}}}]
    (hash-cache/set-values cache cache-key cached-index-names)

    (testing "Testing get granule index names"
      (is (= {:small_collections "1_small_collections"}
             (#'search-index/get-granule-index-names context))))

    (testing "collection concept id to index name"
      (let [query (qm/query {:concept-type :granule
                             :condition (qm/string-conditions :concept-id ["C1200000001-PROV1"] true)
                             :page-size 1})]
        (is (= "1_small_collections,1_c*_prov1" (#'search-index/get-granule-indexes context query)))))

    (testing "provider id to index name"
      (let [query (qm/query {:concept-type :granule
                             :condition (qm/string-conditions :provider ["PROV1"] true)
                             :page-size 2})]
        (is (= "1_small_collections,1_c*_prov1" (#'search-index/get-granule-indexes context query)))))

    (testing "all granule to index name"
      (let [query (qm/query {:concept-type :granule
                              :condition (qm/string-conditions :provider-id ["PROV1"] true)
                              :page-size 3})]
         (is (= "1_c*,1_small_collections,-1_collections*" (#'search-index/get-granule-indexes context query)))))))
