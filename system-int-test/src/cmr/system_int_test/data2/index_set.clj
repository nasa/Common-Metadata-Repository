(ns cmr.system-int-test.data2.index-set)

(def sample-index-set-id 5)
(def sample-index-set
  {:index-set
   {:name "cmr-base-index-set-updated"
    :id sample-index-set-id
    :create-reason "updated index set from sample index"
    :collection {:indexes
                 [{:name "COLL2-PROV1"
                   :settings {:index {:number_of_shards 1,
                                      :number_of_replicas 0,
                                      :refresh_interval "20s"}}}]
                 :mapping {:dynamic "strict",
                           :_source {:enabled true},
                           :properties {:concept-id  {:type "keyword" :norms false :index_options "docs"},
                                        :entry-title {:type "keyword" :norms false :index_options "docs"}}}}
    :granule {:indexes
              [{:name "small_collections"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}
               {:name "C6-PROV3"
                :settings {:index {:number_of_shards 1,
                                   :number_of_replicas 0,
                                   :refresh_interval "10s"}}}]
              :individual-index-settings {:index {:number_of_shards 1,
                                                  :number_of_replicas 0,
                                                  :refresh_interval "10s"}}
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                     :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    :generic-citation {:indexes
                       [{:name "generic-citation"
                         :settings {:index {:number_of_shards 3,
                                            :number_of_replicas 1,
                                            :refresh_interval "1s"}}}]
                       :mapping {:dynamic "strict",
                                 :_source {:enabled true},
                                 :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                              :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    :service {:indexes
              [{:name "services"
                :settings {:index {:number_of_shards 5,
                                   :number_of_replicas 1,
                                   :max_result_window 1000000,
                                   :refresh_interval "1s"}}}]
              :mapping {:dynamic "strict",
                        :_source {:enabled true},
                        :properties {:concept-id {:type "keyword" :norms false :index_options "docs"},
                                     :collection-concept-id {:type "keyword" :norms false :index_options "docs"}}}}
    ;; added concepts list
    :concepts {
               :generic-citation {:generic-citation "5_generic_citation"}
               :granule {:small_collections "5_small_collections"
                         :C6-PROV3 "5_c6_prov3"}
               :collection {:COLL2-PROV1 "5_coll2_prov1"}
               :service {:services "5_services"}
               :autocomplete {},
               :deleted-granule {},
               :generic-citation-draft {},
               :generic-collection-draft {},
               :generic-data-quality-summary {},
               :generic-data-quality-summary-draft {},
               :generic-grid {},
               :generic-grid-draft {},
               :generic-order-option {},
               :generic-order-option-draft {},
               :generic-service-draft {},
               :generic-tool-draft {},
               :generic-variable-draft {},
               :generic-visualization {},
               :generic-visualization-draft {},
               :subscription {},
               :tag {},
               :tool {},
               :variable {}}
    }})