(ns cmr.indexer.test.data.elasticsearch
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.index-set :as i]
   [cmr.indexer.data.index-set-generics :as index-set-gen]))

(def test-index-set
  "A real copy of an index set from UAT with the mappings replaced to be smaller and reduce churn"
  {:index-set
   (merge
    {:name "cmr-base-index-set",
     :id 1,
     :create-reason "indexer app requires this index set",
     :collection {:indexes [{:name "collections-v2",
                             :settings i/collection-setting-v2}
                            {:name "all-collection-revisions",
                             :settings i/collection-setting-v1}]
                  :mapping i/collection-mapping},
     :deleted-granule {:indexes [{:name "deleted_granules",
                                  :settings i/deleted-granule-setting}]
                       :mapping i/deleted-granule-mapping}
     :granule {:indexes
               [{:name "small_collections",
                 :settings i/granule-settings-for-small-collections-index}

                {:name "C274209-USGS_EROS",
                 :settings i/granule-settings-for-individual-indexes}
                {:name "C274211-USGS_EROS",
                 :settings i/granule-settings-for-individual-indexes}],
               :individual-index-settings i/granule-settings-for-individual-indexes
               :mapping i/granule-mapping}
     :tag {:indexes [{:name "tags",
                      :settings i/tag-setting}],
           :mapping i/tag-mapping},
     :variable {:indexes [{:name "variables",
                           :settings i/variable-setting}
                          {:name "all-variable-revisions",
                           :settings i/variable-setting}],
                :mapping i/variable-mapping},
     :service {:indexes [{:name "services",
                          :settings i/service-setting}
                         {:name "all-service-revisions",
                          :settings i/service-setting}],
               :mapping i/service-mapping},
     :tool {:indexes [{:name "tools",
                       :settings i/tool-setting}
                      {:name "all-tool-revisions",
                       :settings i/tool-setting}],
            :mapping i/tool-mapping},
     :subscription {:indexes [{:name "subscriptions",
                               :settings i/subscription-setting}
                              {:name "all-subscription-revisions",
                               :settings i/subscription-setting}],
                    :mapping i/subscription-mapping},
     :concepts {:collection {:collections "1_collections_v2",
                             :all-collection-revisions "1_all_collection_revisions"},
                :granule {:small_collections "1_small_collections",
                          :C274209-USGS_EROS "1_c274209_usgs_eros",
                          :C274211-USGS_EROS "1_c274211_usgs_eros"},
                :tag {:tags "1_tags"}
                :variable {:variables "1_variables"}}
     :autocomplete {:indexes [{:name "autocomplete"
                               :settings i/autocomplete-settings}]
                    :mapping i/autocomplete-mapping}}
    ;; merge into the set of indexes all the configured generic documents
    (index-set-gen/generic-mappings-generator))})

(def valid-item-resp
  "Response for an item that is successfullly indexed during bulk indexing."
  {:index {:_index "1_collections_v2"
           :_type "collection"
           :_id "C1216143440-NSIDC_TS1"
           :status 201}})

(def invalid-item-resp
  "Response for an item that fails indexing during bulk indexing."
  {:index {:_index "1_collections_v2"
           :_type "collection"
           :_id "C1216143440-NSIDC_TS1"
           :status 400
           :error "StrictDynamicMappingException[mapping set to strict, dynamic introduction of [] within [attributes] is not allowed]"}})

(defn- some-failed-bulk-index-response
  "Returns a response mimicking the elastich bulk response with a failing item."
  [num-items]
  (when (> num-items 1)
    (concat [invalid-item-resp] (take (dec num-items) (repeatedly valid-item-resp)))))

(deftest bulk-index-continues-on-error-test
  (testing "400 in response doesn't throw exception"
    ;; Just call handle-bulk-index-reponse to demonstrate it doesn't throw exception
    (let [fake-resp (some-failed-bulk-index-response 1000)]
      (try
        (is (= nil (#'es/handle-bulk-index-response fake-resp)))
        (catch Exception e (is (= nil e) (.getMessage e)))))))

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
    (is (not (es/requires-update?
              test-index-set
              (i/index-set (i/index-set->extra-granule-indexes test-index-set))))))
  (testing "Updates required from individual index settings"
    (is (es/requires-update? (update-in test-index-set [:index-set :granule] dissoc :individual-index-settings)
                             (i/index-set (i/index-set->extra-granule-indexes test-index-set))))))

(deftest reconcile-resharded-index-test
  (let [initial-index-set
        {:index-set
         {:service
          {:indexes
           [{:name "services_2_shards"
             :settings {:index {:number_of_shards 2
                                :number_of_replicas 1}}}
            {:name "all-service-revisions"
             :settings {:index {:number_of_shards 5
                                :number_of_replicas 1}}}]}
          :collection
          {:indexes
           [{:name "collections-v2"
             :settings {:index {:number_of_shards 5
                                :number_of_replicas 1
                                :refresh_interval "1s"}}}
            {:name "all-collection-revisions"
             :settings {:index {:number_of_shards 5
                                :number_of_replicas 1}}}]}}}

        ;; Case 1: same name exists — should NOT update anything
        resharded-already-exists
        [{:concept-type "service"
          :index-key "services"
          :index-name-without-id "services_2_shards"
          :num-shards 2}]

        ;; Case 2: new resharded version — should update canonical
        resharded-new
        [{:concept-type "collection"
          :index-key "collections-v2"
          :index-name-without-id "collections_v2_3_shards"
          :num-shards 3}]]

    (testing "does nothing when the resharded index already exists"
      (let [result (es/reconcile-resharded-index
                    initial-index-set resharded-already-exists)
            updated-index (get-in result [:index-set :service :indexes 0])]
        (is (= "services_2_shards" (:name updated-index)))
        (is (= 2 (get-in updated-index [:settings :index :number_of_shards])))
        (is (= 1 (get-in updated-index [:settings :index :number_of_replicas])))))

    (testing "updates canonical index when resharded version not present, preserving other settings"
      (let [result (es/reconcile-resharded-index
                    initial-index-set resharded-new)
            updated-index (first (filter #(= "collections_v2_3_shards" (:name %))
                                         (get-in result [:index-set :collection :indexes])))]
        (is (= "collections_v2_3_shards" (:name updated-index)))
        (is (= 3 (get-in updated-index [:settings :index :number_of_shards]))
            "number_of_shards should be updated to the new shard count")
        (is (= 1 (get-in updated-index [:settings :index :number_of_replicas]))
            "number_of_replicas should be preserved")
        (is (= "1s" (get-in updated-index [:settings :index :refresh_interval]))
            "custom settings should be preserved")))))
