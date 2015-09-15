(ns cmr.system-int-test.search.tagging.tag-search-test
  "This tests associating tags with collections."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {}))


(deftest search-for-tags-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400, :errors ["Parameter [foo] was not recognized."]}
           (tags/search {:foo "bar"}))))
  (testing "Unsupported parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting tags."]}
           (tags/search {:sort-key "concept_id"})))
    (is (= {:status 400, :errors ["Parameter [provider] was not recognized."]}
           (tags/search {:provider "foo"})))
    (is (= {:status 400, :errors ["Parameter [entry_title] was not recognized."]}
           (tags/search {:entry_title "foo"})))))

(deftest search-for-tags-test
  (let [user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")
        tag1 (tags/save-tag user1-token (tags/make-tag
                                          {:namespace "Namespace1"
                                           :value "Value1"
                                           :category "Category1"}))
        tag2 (tags/save-tag user2-token (tags/make-tag
                                          {:namespace "Namespace1"
                                           :value "Value2"}))
        tag3 (tags/save-tag user1-token (tags/make-tag
                                          {:namespace "Namespace2"
                                           :value "Value1"
                                           :category "Category2"}))
        tag4 (tags/save-tag user2-token (tags/make-tag
                                          {:namespace "Namespace2"
                                           :value "Value2"
                                           :category "Category2"}))
        tag5 (tags/save-tag user1-token (tags/make-tag
                                          {:namespace "Namespace Other"
                                           :value "Value Other"}))
        all-tags [tag1 tag2 tag3 tag4 tag5]]
    (index/wait-until-indexed)

    ;; TODOS for later user stories
    ;; Test searching by:
    ;; - namespace
    ;; - value
    ;; - category
    ;; - originator-id
    ;; - combinations of things

    (tags/assert-tag-search 5 all-tags (tags/search {}))))

(deftest tag-paging-search-test
  (let [token (e/login (s/context) "user1")
        num-tags 20
        tags (->> (range num-tags)
                  (map #(tags/make-tag {:value (str "value" %)}))
                  (map #(tags/save-tag token %))
                  tags/sort-expected-tags
                  vec)]
    (index/wait-until-indexed)

    (testing "Fetch default page size"
      (tags/assert-tag-search num-tags (take 10 tags) (tags/search {})))

    (testing "Fetch entire page"
      (tags/assert-tag-search num-tags tags (tags/search {:page-size 20})))

    (testing "Page through results"
      (doseq [n (range 4)
              :let [expected-page (subvec tags (* n 5) (* (inc n) 5))]]
        (tags/assert-tag-search num-tags expected-page (tags/search {:page-size 5
                                                                     :page-num (inc n)}))))

    (testing "After last page"
      (tags/assert-tag-search num-tags [] (tags/search {:page-size 5 :page-num 5})))))

