(ns cmr.system-int-test.search.tagging.tag-search-test
  "This tests associating tags with collections."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are2]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

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
    (are [field option]
         (= {:status 400
             :errors [(format "Option [%s] is not supported for param [%s]" option (name field))]}
            (tags/search {field "foo" (format "options[%s][%s]" (name field) option) true}))
         ;; tag-key and originator-id do not support ignore case because they are always case insensitive
         :tag_key "and"
         :tag_key "ignore_case"
         :originator_id "and"
         :originator_id "ignore_case")))

(deftest search-for-tags-test
  (let [user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")
        tag1 (tags/save-tag user1-token (tags/make-tag {:tag-key "TAG1"}))
        tag2 (tags/save-tag user2-token (tags/make-tag {:tag-key "Tag2"}))
        tag3 (tags/save-tag user1-token (tags/make-tag {:tag-key "tag3"}))
        tag4 (tags/save-tag user2-token (tags/make-tag {:tag-key "uv.other"}))
        all-tags [tag1 tag2 tag3 tag4]]
    (index/wait-until-indexed)

    (are2 [expected-tags query]
          (tags/assert-tag-search expected-tags (tags/search query))

          "Find all"
          all-tags {}

          ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
          ;; tag-key Param
          "By tag-key case insensitive - lower case"
          [tag1] {:tag-key "tag1"}

          "By tag-key case insensitive - upper case"
          [tag1] {:tag-key "TAG1"}

          "By tag-key case insensitive - mixed case"
          [tag1] {:tag-key "TaG1"}

          "By tag-key Pattern"
          [tag4] {:tag-key "*other" "options[tag-key][pattern]" true}

          ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
          ;; Originator Id Param
          "By Originator Id - Ignore Case Default" ;; Case sensitive not supported
          [tag1 tag3] {:originator-id "USER1"}

          "By Originator Id - Pattern"
          [tag2 tag4] {:originator-id "*2" "options[originator-id][pattern]" true}

          ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
          ;; Parameter Combinations
          "Combination of tag-key and originator-id"
          [tag2] {:tag-key "tag?" "options[tag-key][pattern]" true :originator-id "user2"}

          "Combination with multiple values"
          [tag1] {:tag-key ["tag1" "tag2"] :originator-id "user1"})))

(deftest tag-paging-search-test
  (let [token (e/login (s/context) "user1")
        num-tags 20
        tags (->> (range num-tags)
                  (map #(tags/make-tag {:tag-key (str "tag" %)}))
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

(deftest deleted-tags-not-found-test
  (let [user1-token (e/login (s/context) "user1")
        tag1 (tags/save-tag user1-token (tags/make-tag {:tag-key "tag1"}))
        tag2 (tags/save-tag user1-token (tags/make-tag {:tag-key "tag2"}))
        all-tags [tag1 tag2]]
    (index/wait-until-indexed)

    ;; Now I should find the all tags when searching
    (tags/assert-tag-search all-tags (tags/search {}))

    ;; Delete tag1
    (tags/delete-tag user1-token (:tag-key tag1))

    (index/wait-until-indexed)

    ;; Now searching tags finds nothing
    (tags/assert-tag-search [tag2] (tags/search {}))

    ;; resave the tag
    (let [tag1-3 (tags/save-tag user1-token (dissoc tag1 :revision-id :concept-id))]

      (index/wait-until-indexed)

      ;; Now I should find the tag when searching
      (tags/assert-tag-search [tag1-3 tag2] (tags/search {})))))

(deftest retrieve-concept-by-tag-concept-id-test
  (let [token (e/login (s/context) "user1")
        {:keys [concept-id]} (tags/create-tag token (tags/make-tag {:tag-key "tag1"}))
        {:keys [status errors]} (search/get-search-failure-xml-data
                                  (search/retrieve-concept
                                    concept-id nil {:throw-exceptions true}))]
    (testing "Retrieve concept by tag concept-id is invalid"
      (is (= [400 ["Retrieving concept by concept id is not supported for concept type [tag]."]]
             [status errors])))))
