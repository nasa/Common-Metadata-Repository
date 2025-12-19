(ns cmr.elastic-utils.test.es-helper
  "Test for cmr.elastic-utils.es-helper"
  (:require
    [clojure.test :refer [deftest is testing]]
    [clojurewerkz.elastisch.rest :as rest]
    [cmr.elastic-utils.es-helper :as es-helper]))

(def reindex-resp-example
  {:nodes
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
       :action "indices:data/write/reindex"}}}}})

(deftest test-extract-descriptions-from-reindex-resp
  (let [extract-fn #'cmr.elastic-utils.es-helper/extract-descriptions-from-reindex-resp
        expected-desc-list ["reindex from [1_c1111111111_prov] to [1_c1111111111_prov_10_shards][_doc]",
                            "reindex from [1_c000000_prov] to [1_c00000_prov_20_shards][_doc]",
                            "reindex from [1_c2222_prov] to [1_c2222_prov_5_shards][_doc]"]]
    (testing "get correct list of descriptions"
      (is (= expected-desc-list (extract-fn reindex-resp-example))))
    (testing "empty node map gives empty desc list"
      (is (empty? (extract-fn {:nodes {}}))))
    (testing "empty resp gives empty desc list"
      (is (empty? (extract-fn {}))))))

(deftest test-reindexing-still-in-progress
  (testing "when uri is invalid, then give error message and throw exception"
      (is (thrown? Exception (es-helper/reindexing-still-in-progress? nil "test-index"))))
  (testing "when error resp from elasticsearch, then give error message and throw exception"
    (with-redefs [rest/url-with-path (fn [_ _] "http://url.com")
                  rest/get (fn [_ _] (throw (ex-info "Elasticsearch failure"
                                                          {:status 500 :cause-exception "incorrect url"})))]
      (is (thrown? Exception (es-helper/reindexing-still-in-progress? nil "test-index")))))
  (testing "when reindexing descriptions does not include index, then return false"
    (with-redefs [rest/url-with-path (fn [_ _] "http://url.com")
                  rest/get (fn [_ _] reindex-resp-example)]
      (is (= false (es-helper/reindexing-still-in-progress? nil "index-name-not-in-resp")))))
  (testing "when reindexing description does include index, then return true"
    (with-redefs [rest/url-with-path (fn [_ _] "http://url.com")
                  rest/get (fn [_ _] reindex-resp-example)]
      (is (= true (es-helper/reindexing-still-in-progress? nil "1_c1111111111_prov"))))))
