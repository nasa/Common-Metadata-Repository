(ns cmr.indexer.test.services.index-service
  "Tests for index service"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.index-set :as idx-set]
   [cmr.indexer.indexer-util :as indexer-util]
   [cmr.indexer.services.index-set-service :as index-set-svc]
   [cmr.indexer.services.index-service :as index-svc]))

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

(deftest cascade-collection-delete-test
  (let [context {}
        concept-mapping-types {:granule "granule"}
        concept-id "C1234-PROV1"
        revision-id 3
        small-collections-index "1_small_collections"
        individual-index "1_c1234_prov1"]
    (testing "removes index-set metadata after successfully deleting an individual granule index"
      (let [cleanup-calls (atom [])]
        (with-redefs [idx-set/get-concept-type-index-names
                      (fn [_context]
                        {:index-names {:granule {:small_collections small-collections-index}}})
                      idx-set/get-granule-index-names-for-collection
                      (fn [_context coll-concept-id]
                        (is (= concept-id coll-concept-id))
                        [individual-index])
                      es/delete-granule-index
                      (fn [_context index]
                        (is (= individual-index index))
                        {:status 200})
                      index-set-svc/remove-collection-granule-index-if-exists
                      (fn [_context coll-concept-id]
                        (swap! cleanup-calls conj coll-concept-id))
                      index-svc/reindex-associated-variables
                      (fn [_context _concept-id _revision-id])]
          (#'index-svc/cascade-collection-delete context concept-mapping-types concept-id revision-id)
          (is (= [concept-id] @cleanup-calls)))))

    (testing "does not remove index-set metadata when granules are in small_collections"
      (let [delete-by-query-calls (atom [])
            cleanup-calls (atom [])]
        (with-redefs [idx-set/get-concept-type-index-names
                      (fn [_context]
                        {:index-names {:granule {:small_collections small-collections-index}}})
                      idx-set/get-granule-index-names-for-collection
                      (fn [_context coll-concept-id]
                        (is (= concept-id coll-concept-id))
                        [small-collections-index])
                      indexer-util/context->conn
                      (fn [_context elastic-name]
                        (is (= es-config/gran-elastic-name elastic-name))
                        nil)
                      es-helper/delete-by-query
                      (fn [_conn index mapping query]
                        (swap! delete-by-query-calls conj [index mapping query])
                        {:status 200})
                      es/delete-granule-index
                      (fn [& _args]
                        (is false "delete-granule-index should not be called for small_collections"))
                      index-set-svc/remove-collection-granule-index-if-exists
                      (fn [& _args]
                        (swap! cleanup-calls conj :called))
                      index-svc/reindex-associated-variables
                      (fn [_context _concept-id _revision-id])]
          (#'index-svc/cascade-collection-delete context concept-mapping-types concept-id revision-id)
          (is (= 1 (count @delete-by-query-calls)))
          (is (empty? @cleanup-calls)))))

    (testing "does not remove index-set metadata when deleting an individual granule index fails"
      (let [cleanup-calls (atom [])]
        (with-redefs [idx-set/get-concept-type-index-names
                      (fn [_context]
                        {:index-names {:granule {:small_collections small-collections-index}}})
                      idx-set/get-granule-index-names-for-collection
                      (fn [_context coll-concept-id]
                        (is (= concept-id coll-concept-id))
                        [individual-index])
                      es/delete-granule-index
                      (fn [_context index]
                        (is (= individual-index index))
                        {:status 500})
                      index-set-svc/remove-collection-granule-index-if-exists
                      (fn [& _args]
                        (swap! cleanup-calls conj :called))
                      index-svc/reindex-associated-variables
                      (fn [_context _concept-id _revision-id])]
          (#'index-svc/cascade-collection-delete context concept-mapping-types concept-id revision-id)
          (is (empty? @cleanup-calls)))))))
