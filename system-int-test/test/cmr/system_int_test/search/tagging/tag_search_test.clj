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

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {})
                                    tags/grant-all-tag-fixture]))

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
           (tags/search {:entry_title "foo"}))))

  (testing "Unsupported options"
    (are [field]
         (= {:status 400
             :errors [(format "Option [and] is not supported for param [%s]" (name field))]}
            (tags/search {field "foo" (format "options[%s][and]" (name field)) true}))
         :namespace :category :value :originator_id)

    (testing "Originator id does not support ignore case because it is always case insensitive"
      (is (= {:status 400
              :errors ["Option [ignore_case] is not supported for param [originator_id]"]}
             (tags/search {:originator-id "foo" "options[originator-id][ignore-case]" true}))))))

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

    (are2 [expected-tags query]
         (tags/assert-tag-search expected-tags (tags/search query))

         "Find all"
         all-tags {}

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Parameter Combinations
         "Combination of namespace and value"
         [tag1] {:namespace "namespace1" :value "value1"}

         "Combination with multiple values"
         [tag1 tag3] {:namespace ["namespace1" "namespace2" "foo"] :value "value1"}


         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Namespace Param

         "By Namespace - Ignore Case Default"
         [tag1 tag2] {:namespace "namespace1"}

         "By Namespace Case Sensitive - no match"
         [] {:namespace "namespace1" "options[namespace][ignore-case]" false}

         "By Namespace Case Sensitive - matches"
         [tag1 tag2] {:namespace "Namespace1" "options[namespace][ignore-case]" false}

         "By Namespace Pattern"
         [tag5] {:namespace "*other" "options[namespace][pattern]" true}

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Value Param

         "By Value - Ignore Case Default"
         [tag1 tag3] {:value "value1"}

         "By Value Case Sensitive - no match"
         [] {:value "value1" "options[value][ignore-case]" false}

         "By Value Case Sensitive - matches"
         [tag1 tag3] {:value "Value1" "options[value][ignore-case]" false}

         "By Value Pattern"
         [tag5] {:value "*other" "options[value][pattern]" true}

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Category Param

         "By Category - Ignore Case Default"
         [tag3 tag4] {:category "category2"}

         "By Category Case Sensitive - no match"
         [] {:category "category2" "options[category][ignore-case]" false}

         "By Category Case Sensitive - matches"
         [tag3 tag4] {:category "Category2" "options[category][ignore-case]" false}

         "By Category Pattern"
         [tag1] {:category "*1" "options[category][pattern]" true}

         ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
         ;; Originator Id Param

         "By Originator Id - Ignore Case Default" ;; Case sensitive not supported
         [tag1 tag3 tag5] {:originator-id "USER1"}

         "By Originator Id - Pattern"
         [tag2 tag4] {:originator-id "*2" "options[originator-id][pattern]" true})))

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

