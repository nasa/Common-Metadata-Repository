(ns cmr.search.test.unit.data.complex-to-simple-converters.has-granules-or-cwic-test
  (:require 
   [clojure.test :refer [deftest is testing]]
   [cmr.common.cache :as cache]
   [cmr.common.services.search.query-model :as cqm]
   [cmr.elastic-utils.search.query-transform :as c2s]
   [cmr.search.models.query :as query]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature :as has-gran-or-cwic-feature]
   [cmr.search.services.query-execution.has-granules-results-feature :as has-granules-feature]))

;; Test context with both caches (has-granules-cache has-granules-or-cwic) have values - normal case
(def test-context
  {:system
   {:caches
    {has-gran-or-cwic-feature/has-granules-or-cwic-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] {"C1" true
                         "C2" true
                         "C3" true})
       (get-value [_ _ _] {"C1" true
                           "C2" true
                           "C3" true})
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))
     has-granules-feature/has-granule-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] {"C1" true
                         "C2" true
                         "C3" true
                         "C4" true})
       (get-value [_ _ _] {"C1" true
                           "C2" true
                           "C3" true
                           "C4" true})
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))}}})

;; Test context with both caches (has-granules-cache has-granules-or-cwic) empty
(def test-context-empty-caches
  {:system
   {:caches
    {has-gran-or-cwic-feature/has-granules-or-cwic-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] nil)
       (get-value [_ _ _] nil)
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))
     has-granules-feature/has-granule-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] nil)
       (get-value [_ _ _] nil)
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))}}})

(deftest has-granules-or-cwic-condition-test
  (testing "HasGranulesOrCwicCondition with has-granules-or-cwic true"
    (let [condition (query/map->HasGranulesOrCwicCondition {:has-granules-or-cwic true})
          result (c2s/reduce-query-condition condition test-context)]
      (is (= (cqm/string-conditions :concept-id ["C1" "C2" "C3"] true) result))))

   (testing "HasGranulesOrCwicCondition with has-granules-map"
     (let [condition (query/map->HasGranulesOrCwicCondition {:has-granules-or-cwic false})
           result (c2s/reduce-query-condition condition test-context)]
       (is (= (cqm/negated-condition (cqm/string-conditions :concept-id ["C1" "C2" "C3" "C4"] true)) result))))

  (testing "HasGranulesOrCwicCondition with has-granules-or-cwic true and has-granules-or-cwic empty"
    (let [condition (query/map->HasGranulesOrCwicCondition {:has-granules-or-cwic true})
          result (c2s/reduce-query-condition condition test-context-empty-caches)]
      (is (= cqm/match-none result))))

  (testing "HasGranulesOrCwicCondition with has-granules-map empty"
    (let [condition (query/map->HasGranulesOrCwicCondition {:has-granules-or-cwic false})
          result (c2s/reduce-query-condition condition test-context-empty-caches)]
      (is (= (cqm/negated-condition cqm/match-none) result)))))

;; Test context with both caches (has-granules-cache has-granules-or-opensearch) have values - normal case
(def test-context-opensearch
  {:system
   {:caches
    {has-gran-or-cwic-feature/has-granules-or-opensearch-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] {"C1" true
                         "C2" true
                         "C3" true})
       (get-value [_ _ _] {"C1" true
                           "C2" true
                           "C3" true})
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))
     has-granules-feature/has-granule-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] {"C1" true
                         "C2" true
                         "C3" true
                         "C4" true})
       (get-value [_ _ _] {"C1" true
                           "C2" true
                           "C3" true
                           "C4" true})
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))}}})

;; Test context with both caches (has-granules-cache has-granules-or-cwic) empty
(def test-context-opensearch-empty-caches
  {:system
   {:caches
    {has-gran-or-cwic-feature/has-granules-or-opensearch-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] nil)
       (get-value [_ _ _] nil)
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))
     has-granules-feature/has-granule-cache-key
     ;; minimal fake hash cache that satisfies the protocol
     (reify cache/CmrCache
       (key-exists [_ _] true)
       (get-keys [_] nil)
       (get-value [_ _] nil)
       (get-value [_ _ _] nil)
       (reset [_] nil)
       (set-value [_ _ _] nil)
       (cache-size [_] 0))}}})

(deftest has-granules-or-opensearch-condition-test
  (testing "HasGranulesOrOpenSearchCondition with has-granules-or-opensearch true"
     (let [condition (query/map->HasGranulesOrOpenSearchCondition {:has-granules-or-opensearch true})
           result (c2s/reduce-query-condition condition test-context-opensearch)]
       (is (= (cqm/string-conditions :concept-id ["C1" "C2" "C3"] true) result))))
  
   (testing "HasGranulesOrOpenSearchCondition with has-granules-map"
     (let [condition (query/map->HasGranulesOrOpenSearchCondition {:has-granules-or-opensearch false})
           result (c2s/reduce-query-condition condition test-context-opensearch)]
       (is (= (cqm/negated-condition (cqm/string-conditions :concept-id ["C1" "C2" "C3" "C4"] true)) result))))
  
   (testing "HasGranulesOrOpenSearchCondition with has-granules-or-cwic true and has-granules-or-opensearch empty"
     (let [condition (query/map->HasGranulesOrOpenSearchCondition {:has-granules-or-opensearch true})
           result (c2s/reduce-query-condition condition test-context-opensearch-empty-caches)]
       (is (= cqm/match-none result))))
  
   (testing "HasGranulesOrOpenSearchCondition with has-granules-map empty"
     (let [condition (query/map->HasGranulesOrOpenSearchCondition {:has-granules-or-opensearch false})
           result (c2s/reduce-query-condition condition test-context-opensearch-empty-caches)]
       (is (= (cqm/negated-condition cqm/match-none) result)))))
