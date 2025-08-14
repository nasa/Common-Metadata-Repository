(ns cmr.elastic-utils.test.es-index
  "Tests for the cmr.elastic-utils.search.es-index namespace"
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.config :as config]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.es-index :as es-index]))

(def gran-cluster cmr.elastic-utils.config/gran-elastic-name)
(def non-gran-cluster config/non-gran-elastic-name)

(deftest test-query->execution-params
  (let [query->execution-params #'es-index/query->execution-params
        condition (gc/or-conds (map #(qm/string-conditions :consortiums [%])
                                    ["CWIC" "FEDEO" "GEOSS" "CEOS" "EOSDIS"]))]
    (testing "query include remove-source"
      (let [query (qm/query {:concept-type :collection
                             :result-format :xml
                             :condition condition
                             :page-size :unlimited
                             :remove-source true})
            execution-params (query->execution-params query)]
        (is (= false
               (:_source execution-params)))))
    (testing "query doesn't include remove-source"
      (let [query (qm/query {:concept-type :collection
                             :result-format :xml
                             :condition condition
                             :page-size :unlimited})
            execution-params (query->execution-params query)]
        (is (not (= false
                    (:_source execution-params))))))))

(deftest test-get-es-cluster-name-from-index-name
  (testing "All excluded indices always return non-gran cluster regardless of other patterns"
    (let [excluded #{"collection_search_alias" "1_collections_v2"}]
      (doseq [excluded-index excluded]
        (is (= non-gran-cluster
               (es-index/get-es-cluster-name-from-index-name excluded-index))))))

  (testing "All indices starting with '1_c' (but not excluded) return gran cluster"
    (let [test-indices ["1_c" "1_ca" "1_collections" "1_custom" "1_c123"]]
      (doseq [index test-indices]
        (is (= gran-cluster
               (es-index/get-es-cluster-name-from-index-name index))))))

  (testing "Random/default indices should return non-gran cluster"
    (let [random #{"some_index" "users" "metadata" ""}]
      (doseq [index random]
        (is (= non-gran-cluster (es-index/get-es-cluster-name-from-index-name index)))))))