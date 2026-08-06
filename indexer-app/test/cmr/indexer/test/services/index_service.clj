(ns cmr.indexer.test.services.index-service
  "Tests for index service"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.indexer.common.index-set-util :as idx-set-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.index-set :as idx-set]
   [cmr.indexer.indexer-util :as indexer-util]
   [cmr.indexer.services.index-service :as index-svc]
   [cmr.indexer.services.index-set-service :as idx-set-svc]))

(deftest index-concept-invalid-input-test
  (testing "invalid input"
    (are [concept-id revision-id]
         (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Concept-id .* cannot be null"
           (index-svc/index-concept-by-concept-id-revision-id {} concept-id revision-id true))

         nil 1
         nil nil)))

(deftest determine-reindex-batch-size-test
  (testing "determining the reindexing batch size based on the given provider."
    (are3 [expected-size provider]
      (is (= expected-size (index-svc/determine-reindex-batch-size provider)))

      "Testing a provider that has normal sized collections."
      (index-svc/collection-reindex-batch-size) "NSIDC"

      "Testing a provider that has large sized collections."
      (index-svc/collection-large-file-providers-reindex-batch-size) "GHRSSTCWIC")))

(deftest index-log-size-test
  (testing "Make sure that any new time to visibility index log is not larger then the original"

    ;; The time to visibility log can print in the millions per day and there are two different
    ;; versions of this log depending on the value of defconfig reduced-indexer-log. When true a
    ;; JSON version of this log is printed which contains more information but should still be
    ;; shorter as Splunk storage is expensive.

    (let [concept-id "G1001055217-ASF"
          revision-id "123"
          milliseconds 1024
          all-revisions-index? true
          time-to-visibility-text (var index-svc/time-to-visibility-text) ;; private function
          time-to-visibility-json (var index-svc/time-to-visibility-json) ;; private function
          text (time-to-visibility-text concept-id milliseconds)
          json (time-to-visibility-json concept-id revision-id milliseconds all-revisions-index?)]

      (is (<= (count json) (count text))))))

(deftest time-to-visibility-json-test
  (testing "Ensure that the output of the time-to-vilibility-json function is in fact JSON"
    (let [time-to-visibility-json (var index-svc/time-to-visibility-json) ;; private function
          raw-json (time-to-visibility-json "ACL123-CMR", 1, 1234, false)
          data (json/parse-string raw-json true)]
      (is (some? data) "time-to-visibility-json parsing test")
      (is (= "ACL" (:ct data)) "Checking concept type")
      (is (= "index-vis" (:mg data)) "Checking message id"))))

(defn- cascade-collection-delete-index-set-result
  "Calls cascade-collection-delete with mocked dependencies and returns the index set passed to
  update-index-set, or nil when no index-set update occurs."
  [gran-index-set granule-index-names delete-index-status]
  (let [concept-id "C1234-PROV1"
        updated-index-set (atom nil)]
    (with-redefs [idx-set/get-concept-type-index-names
                  (fn [_context]
                    {:index-names {:granule {:small_collections "1_small_collections"}}})
                  idx-set/get-granule-index-names-for-collection
                  (fn [_context _concept-id]
                    granule-index-names)
                  idx-set-util/get-index-set
                  (fn [_context _elastic-name _index-set-id]
                    gran-index-set)
                  indexer-util/context->conn
                  (fn [_context _cluster-name]
                    nil)
                  es/delete-granule-index
                  (fn [_context _index-name]
                    {:status delete-index-status})
                  es-helper/delete-by-query
                  (fn [& _args]
                    {:status 200})
                  idx-set-svc/validate-requested-index-set
                  (fn [& _args])
                  idx-set-svc/save-combined-index-set-to-mdb
                  (fn [& _args]
                    33)
                  idx-set-svc/update-index-set
                  (fn [_context _elastic-name index-set _revision-id]
                    (reset! updated-index-set index-set)
                    {:status 200})
                  index-svc/reindex-associated-variables
                  (fn [& _args]
                    :ok)]
      (#'index-svc/cascade-collection-delete {} {:granule "granule"} concept-id 7))
    @updated-index-set))

(deftest cascade-collection-delete-index-set-result-test
  (let [concept-id "C1234-PROV1"
        small-index "1_small_collections"
        separate-index "1_c1234_prov1"
        separate-index-set {:index-set
                            {:concepts {:granule {:small_collections small-index
                                                 (keyword concept-id) separate-index}}
                             :granule {:indexes [{:name small-index}
                                                 {:name separate-index
                                                  :number_of_shards 5}]}}}
        small-index-set {:index-set
                         {:concepts {:granule {:small_collections small-index}}
                          :granule {:indexes [{:name small-index}]}}}
        updated-index-set {:index-set
                           {:concepts {:granule {:small_collections small-index}}
                            :granule {:indexes [{:name small-index}]}}}]
    (are3 [expected gran-index-set granule-index-names delete-index-status]
      (is (= expected
             (cascade-collection-delete-index-set-result
              gran-index-set granule-index-names delete-index-status)))

      "when deletion of a separate index succeeds, then cascade-collection-delete 
       removes its mapping and definition from the index set"
      updated-index-set separate-index-set [separate-index] 200

      "when a separate index is missing from Elasticsearch, then cascade-collection-delete 
       removes its stale mapping and definition from the index set"
      updated-index-set separate-index-set [separate-index] 404

      "when deletion of a separate index fails, then cascade-collection-delete 
       does not update the index set"
      nil separate-index-set [separate-index] 500

      "when the collection uses small_collections, then cascade-collection-delete does not update the index set"
      nil small-index-set [small-index] nil)))
