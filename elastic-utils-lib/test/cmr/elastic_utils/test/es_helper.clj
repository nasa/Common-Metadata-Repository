(ns cmr.elastic-utils.test.es-helper
  "Test for cmr.elastic-utils.es-helper"
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.elastic-utils.es-helper :as es-helper]))

(deftest test-extract-descriptions-from-reindex-resp
  (let [extract-fn #'cmr.elastic-utils.es-helper/extract-descriptions-from-reindex-resp
        resp {:nodes
              {:node-name {
                :tasks {
                  :node-name:102917 {
                    :description "reindex from [1_c1111111111_prov] to [1_c1111111111_prov_10_shards][_doc]",
                    :action "indices:data/write/reindex"}}},
               :node-name-2 {
                 :tasks {
                   :node-name-2:00000 {
                    :description "reindex from [1_c000000_prov] to [1_c00000_prov_20_shards][_doc]",
                    :action "indices:data/write/reindex"}
                   :node-name-2:111111 {
                     :description "reindex from [1_c2222_prov] to [1_c2222_prov_5_shards][_doc]",
                     :action "indices:data/write/reindex"}}}
               }}
        expected-desc-list ["reindex from [1_c1111111111_prov] to [1_c1111111111_prov_10_shards][_doc]",
                            "reindex from [1_c000000_prov] to [1_c00000_prov_20_shards][_doc]",
                            "reindex from [1_c2222_prov] to [1_c2222_prov_5_shards][_doc]"]]
    (testing "get correct list of descriptions"
      (is (= expected-desc-list (extract-fn resp))))
    (testing "empty node map gives empty desc list"
      (is (empty (extract-fn {:nodes {}}))))
    (testing "empty resp gives empty desc list"
      (is (empty (extract-fn {}))))))