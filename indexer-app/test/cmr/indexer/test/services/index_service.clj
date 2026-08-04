(ns cmr.indexer.test.services.index-service
  "Tests for index service"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.elastic-utils.es-helper :as es-helper]
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

(deftest when-separate-index-deletes-succeed-then-call-index-set-cleanup-for-each-index
  (let [concept-id "C1234-PROV1"
        cleanup-var (ns-resolve 'cmr.indexer.services.index-set-service
                                'remove-collection-granule-index-if-exists)
        cleanup-calls (atom [])
        redefs-map (cond-> {#'idx-set/get-concept-type-index-names
                            (fn [_context]
                              {:index-names {:granule {:small_collections "1_small_collections"}}})
                            #'idx-set/get-granule-index-names-for-collection
                            (fn [_context _concept-id]
                              ["1_c1234_prov1" "1_c1234_prov1_8_shards"])
                            #'indexer-util/context->conn
                            (fn [_context _cluster-name]
                              nil)
                            #'es/delete-granule-index
                            (fn [_context _index-name]
                              {:status 200})
                            #'es-helper/delete-by-query
                            (fn [& _args]
                              {:status 200})
                            #'index-svc/reindex-associated-variables
                            (fn [& _args]
                              :ok)}
                     cleanup-var
                     (assoc cleanup-var
                            (fn [_context concept-id-param]
                              (swap! cleanup-calls conj concept-id-param)
                              {:status 200})))]
    (is (some? cleanup-var)
        "Expected public function remove-collection-granule-index-if-exists to exist in index-set-service")
    (with-redefs-fn redefs-map
      #(let [cascade-delete-fn (var index-svc/cascade-collection-delete)]
         (cascade-delete-fn {} {:granule "granule"} concept-id 7)))
    (when cleanup-var
      (is (= [concept-id concept-id] @cleanup-calls)
          "Each successful index delete should trigger index-set cleanup"))))

(deftest cascade-collection-delete-does-not-call-index-set-cleanup-for-small-collections-test
  (let [concept-id "C1234-PROV1"
        cleanup-var (ns-resolve 'cmr.indexer.services.index-set-service
                                'remove-collection-granule-index-if-exists)
        cleanup-calls (atom [])
        redefs-map (cond-> {#'idx-set/get-concept-type-index-names
                            (fn [_context]
                              {:index-names {:granule {:small_collections "1_small_collections"}}})
                            #'idx-set/get-granule-index-names-for-collection
                            (fn [_context _concept-id]
                              ["1_small_collections"])
                            #'indexer-util/context->conn
                            (fn [_context _cluster-name]
                              nil)
                            #'es/delete-granule-index
                            (fn [_context _index-name]
                              {:status 200})
                            #'es-helper/delete-by-query
                            (fn [& _args]
                              {:status 200})
                            #'index-svc/reindex-associated-variables
                            (fn [& _args]
                              :ok)}
                     cleanup-var
                     (assoc cleanup-var
                            (fn [_context concept-id-param]
                              (swap! cleanup-calls conj concept-id-param)
                              {:status 200})))]
    (is (some? cleanup-var)
        "Expected public function remove-collection-granule-index-if-exists to exist in index-set-service")
    (with-redefs-fn redefs-map
      #(let [cascade-delete-fn (var index-svc/cascade-collection-delete)]
         (cascade-delete-fn {} {:granule "granule"} concept-id 7)))
    (when cleanup-var
      (is (empty? @cleanup-calls)
          "Collection delete should not trigger index-set cleanup for small_collections path"))))

(deftest cascade-collection-delete-does-not-call-index-set-cleanup-when-separate-index-delete-fails-test
  (let [concept-id "C1234-PROV1"
        cleanup-var (ns-resolve 'cmr.indexer.services.index-set-service
                                'remove-collection-granule-index-if-exists)
        cleanup-calls (atom [])
        redefs-map (cond-> {#'idx-set/get-concept-type-index-names
                            (fn [_context]
                              {:index-names {:granule {:small_collections "1_small_collections"}}})
                            #'idx-set/get-granule-index-names-for-collection
                            (fn [_context _concept-id]
                              ["1_c1234_prov1"])
                            #'indexer-util/context->conn
                            (fn [_context _cluster-name]
                              nil)
                            #'es/delete-granule-index
                            (fn [_context _index-name]
                              {:status 500})
                            #'es-helper/delete-by-query
                            (fn [& _args]
                              {:status 200})
                            #'index-svc/reindex-associated-variables
                            (fn [& _args]
                              :ok)}
                     cleanup-var
                     (assoc cleanup-var
                            (fn [_context concept-id-param]
                              (swap! cleanup-calls conj concept-id-param)
                              {:status 200})))]
    (is (some? cleanup-var)
        "Expected public function remove-collection-granule-index-if-exists to exist in index-set-service")
    (with-redefs-fn redefs-map
      #(let [cascade-delete-fn (var index-svc/cascade-collection-delete)]
         (cascade-delete-fn {} {:granule "granule"} concept-id 7)))
    (when cleanup-var
      (is (empty? @cleanup-calls)
          "Collection delete should not trigger index-set cleanup when separate index deletion fails"))))

(deftest cascade-collection-delete-calls-index-set-cleanup-when-separate-index-already-missing-test
  (let [concept-id "C1234-PROV1"
        cleanup-var (ns-resolve 'cmr.indexer.services.index-set-service
                                'remove-collection-granule-index-if-exists)
        cleanup-calls (atom [])
        redefs-map (cond-> {#'idx-set/get-concept-type-index-names
                            (fn [_context]
                              {:index-names {:granule {:small_collections "1_small_collections"}}})
                            #'idx-set/get-granule-index-names-for-collection
                            (fn [_context _concept-id]
                              ["1_c1234_prov1"])
                            #'indexer-util/context->conn
                            (fn [_context _cluster-name]
                              nil)
                            #'es/delete-granule-index
                            (fn [_context _index-name]
                              {:status 404})
                            #'es-helper/delete-by-query
                            (fn [& _args]
                              {:status 200})
                            #'index-svc/reindex-associated-variables
                            (fn [& _args]
                              :ok)}
                     cleanup-var
                     (assoc cleanup-var
                            (fn [_context concept-id-param]
                              (swap! cleanup-calls conj concept-id-param)
                              {:status 200})))]
    (is (some? cleanup-var)
        "Expected public function remove-collection-granule-index-if-exists to exist in index-set-service")
    (with-redefs-fn redefs-map
      #(let [cascade-delete-fn (var index-svc/cascade-collection-delete)]
         (cascade-delete-fn {} {:granule "granule"} concept-id 7)))
    (when cleanup-var
      (is (= [concept-id] @cleanup-calls)
          "Collection delete should trigger index-set cleanup when separate index is already missing (404)"))))
