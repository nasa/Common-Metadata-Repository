(ns cmr.system-int-test.search.tagging.collection-tag-search-test
  "This tests searching for collections by tag parameters"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       tags/grant-all-tag-fixture]))

(deftest search-for-collections-with-tag-params-validation-test
  (testing "Unsupported options"
    (are [field]
         (= {:status 400
             :errors [(format "Option [and] is not supported for param [%s]" (name field))]}
            (search/find-refs :collection {field "foo" (format "options[%s][and]" (name field)) true}))
         :tag_namespace :tag_category :tag_value :tag_originator_id)

    (testing "Originator id does not support ignore case because it is always case insensitive"
      (is (= {:status 400
              :errors ["Option [ignore_case] is not supported for param [tag_originator_id]"]}
             (search/find-refs :collection {:tag-originator-id "foo"
                                            "options[tag-originator-id][ignore-case]" true}))))))

(deftest search-for-collections-with-tag-json-query-validation-test
  (testing "Unsupported options"
    (are [field]
         (= {:status 400
             :errors ["/condition/tag object instance has properties which are not allowed by the schema: [\"and\"]"]}
            (search/find-refs-with-json-query :collection {} {:tag {field "name" :and true}}))
         :namespace :category :value :originator_id)

    (testing "Originator id does not support ignore case because it is always case insensitive"
      (is
        (= {:status 400
            :errors ["/condition/tag/originator_id instance failed to match exactly one schema (matched 0 out of 2)"
                     "/condition/tag/originator_id instance type (object) does not match any allowed primitive type (allowed: [\"string\"])"
                     "/condition/tag/originator_id object instance has properties which are not allowed by the schema: [\"ignore_case\"]"]}
           (search/find-refs-with-json-query :collection {}
                                             {:tag {:originator_id {:value "foo"
                                                                    :ignore_case true}}}))))))

(deftest search-for-collections-by-tag-params-test
  (let [[c1-p1 c2-p1 c3-p1 c4-p1 c5-p1
         c1-p2 c2-p2 c3-p2 c4-p2 c5-p2] (for [p ["PROV1" "PROV2"]
                                              n (range 1 6)]
                                          (d/ingest p (dc/collection {:entry-title (str "coll" n )})))

        ;; Wait until collections are indexed so tags can be associated with them
        _ (index/wait-until-indexed)

        user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")

        tag1-colls [c1-p1 c1-p2]
        tag2-colls [c2-p1 c2-p2]
        tag3-colls [c3-p1 c3-p2]
        tag4-colls [c4-p1 c4-p2]
        tag5-colls [c5-p1 c5-p2]

        tag1 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "Namespace1" :value "Value1" :category "Category1"})
               tag1-colls)
        tag2 (tags/save-tag
               user2-token
               (tags/make-tag {:namespace "Namespace1" :value "Value2"})
               tag2-colls)
        tag3 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "Namespace2" :value "Value1" :category "Category2"})
               tag3-colls)
        tag4 (tags/save-tag
               user2-token
               (tags/make-tag {:namespace "Namespace2" :value "Value2" :category "Category2"})
               tag4-colls)
        tag5 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "Namespace Other" :value "Value Other"})
               tag5-colls)
        ;; Tag 6 is not associated with any collections
        tag6 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "Namespace1" :value "Value3" :category "Category1"}))]
    (index/wait-until-indexed)

    (testing "All tag parameters with XML references"
      (are2 [expected-tags-colls query]
            (d/refs-match? (distinct (apply concat expected-tags-colls))
                           (search/find-refs :collection query))

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Parameter Combinations
            "Combination of namespace and value"
            [tag1-colls] {:tag-namespace "namespace1" :tag-value "value1"}

            "Combination with multiple values"
            [tag1-colls tag3-colls] {:tag-namespace ["namespace1" "namespace2" "foo"] :tag-value "value1"}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Namespace Param

            "By Namespace - Ignore Case Default"
            [tag1-colls tag2-colls] {:tag-namespace "namespace1"}

            "By Namespace Case Sensitive - no match"
            [] {:tag-namespace "namespace1" "options[tag-namespace][ignore-case]" false}

            "By Namespace Case Sensitive - matches"
            [tag1-colls tag2-colls] {:tag-namespace "Namespace1" "options[tag-namespace][ignore-case]" false}

            "By Namespace Pattern"
            [tag5-colls] {:tag-namespace "*other" "options[tag-namespace][pattern]" true}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Value Param

            "By Value - Ignore Case Default"
            [tag1-colls tag3-colls] {:tag-value "value1"}

            "By Value Case Sensitive - no match"
            [] {:tag-value "value1" "options[tag-value][ignore-case]" false}

            "By Value Case Sensitive - matches"
            [tag1-colls tag3-colls] {:tag-value "Value1" "options[tag-value][ignore-case]" false}

            "By Value Pattern"
            [tag5-colls] {:tag-value "*other" "options[tag-value][pattern]" true}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Category Param

            "By Category - Ignore Case Default"
            [tag3-colls tag4-colls] {:tag-category "category2"}

            "By Category Case Sensitive - no match"
            [] {:tag-category "category2" "options[tag-category][ignore-case]" false}

            "By Category Case Sensitive - matches"
            [tag3-colls tag4-colls] {:tag-category "Category2" "options[tag-category][ignore-case]" false}

            "By Category Pattern"
            [tag1-colls] {:tag-category "*1" "options[tag-category][pattern]" true}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Originator Id Param

            "By Originator Id - Ignore Case Default" ;; Case sensitive not supported
            [tag1-colls tag3-colls tag5-colls] {:tag-originator-id "USER1"}

            "By Originator Id - Pattern"
            [tag2-colls tag4-colls] {:tag-originator-id "*2" "options[tag-originator-id][pattern]" true}))

    (testing "Search collections by tags with JSON query"
      (are2 [expected-tags-colls query]
            (d/refs-match? (distinct (apply concat expected-tags-colls))
                           (search/find-refs-with-json-query :collection {} query))

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Namespace query

            "By Namespace - Ignore Case Default"
            [tag1-colls tag2-colls] {:tag {:namespace "namespace1"}}

            "By Namespace - ignore case explicitly"
            [tag1-colls tag2-colls] {:tag {:namespace {:value "namespace1" :ignore_case true}}}

            "By Namespace Case Sensitive - no match"
            [] {:tag {:namespace {:value "namespace1" :ignore_case false}}}

            "By Namespace Case Sensitive - matches"
            [tag1-colls tag2-colls] {:tag {:namespace {:value "Namespace1" :ignore_case false}}}

            "By Namespace Pattern"
            [tag5-colls] {:tag {:namespace {:value "*other" :pattern true}}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Value query

            "By Value - Ignore Case Default"
            [tag1-colls tag3-colls] {:tag {:value "value1"}}

            "By Value - ignore case explicitly"
            [tag1-colls tag3-colls] {:tag {:value {:value "value1" :ignore_case true}}}

            "By Value Case Sensitive - no match"
            [] {:tag {:value {:value "value1" :ignore_case false}}}

            "By Value Case Sensitive - matches"
            [tag1-colls tag3-colls] {:tag {:value {:value "Value1" :ignore_case false}}}

            "By Value Pattern"
            [tag5-colls] {:tag {:value {:value "*other" :pattern true}}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Category Param

            "By Category - Ignore Case Default"
            [tag3-colls tag4-colls] {:tag {:category "category2"}}

            "By Category - ignore case explicitly"
            [tag3-colls tag4-colls] {:tag {:category {:value "category2" :ignore_case true}}}

            "By Category Case Sensitive - no match"
            [] {:tag {:category {:value "category2" :ignore_case false}}}

            "By Category Case Sensitive - matches"
            [tag3-colls tag4-colls] {:tag {:category {:value "Category2" :ignore_case false}}}

            "By Category Pattern"
            [tag1-colls] {:tag {:category {:value "*1" :pattern true}}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Originator Id Param

            "By Originator Id - Ignore Case Default" ;; Case sensitive not supported
            [tag1-colls tag3-colls tag5-colls] {:tag {:originator_id "USER1"}}

            "By Originator Id - Pattern"
            [tag2-colls tag4-colls] {:tag {:originator_id {:value "*2" :pattern true}}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; JSON Query Conditional constructs: and, or, not

            "By or'ing tags"
            [tag3-colls tag4-colls tag5-colls] {:or [{:tag {:namespace {:value "*other"
                                                                        :pattern true}}}
                                                     {:tag {:category "category2"}}]}

            "By and'ing tags"
            [tag1-colls] {:and [{:tag {:value "value1"}} {:tag {:namespace "namespace1"}}]}

            "By negating ('not') tags"
            [tag3-colls tag4-colls tag5-colls] {:not {:tag {:namespace "Namespace1"}}}

            "By nested 'and', 'or', and 'not' tags"
            [tag4-colls tag5-colls] {:and [{:not {:tag {:namespace "Namespace1"}}}
                                           {:or [{:not {:tag {:namespace "Namespace2"}}}
                                                 {:tag {:value "Value2"}}]}]}


            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Combinations

            "Combination of namespace and value"
            [tag1-colls] {:tag {:namespace "namespace1" :value "value1"}}

            "Combination with multiple tag queries"
            [tag1-colls tag3-colls]
            {:or [{:tag {:namespace "namespace1" :value "value1"}}
                  {:tag {:namespace {:value "namespace2" :ignore_case true} :value "value1"}}
                  {:tag {:namespace "foo" :value "value1"}}]}))

    (testing "Combination of tag parameters and other collection conditions"
      (is (d/refs-match? [c1-p1 c2-p1] (search/find-refs :collection {:tag-namespace "namespace1"
                                                                      :provider "PROV1"}))))))

(defn- add-tags-to-collections
  "Returns the collections with the tags associated based on the given dataset-id-tags mapping"
  [colls dataset-id-tags]
  (map (fn [c]
         (if-let [tags (dataset-id-tags (:dataset-id c))]
           (assoc c :tags tags)
           c))
       colls))

(deftest search-for-collections-with-include-tags-test
  (let [[coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                                    (d/ingest "PROV1" (dc/collection {:entry-title (str "coll" n )})))
        coll5 (d/ingest "PROV2" (dc/collection))
        all-prov1-colls [coll1 coll2 coll3 coll4]

        ;; Wait until collections are indexed so tags can be associated with them
        _ (index/wait-until-indexed)

        user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")

        tag1-colls [coll1 coll5]
        tag2-colls [coll1 coll2]
        tag3-colls [coll3]

        tag1 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "Namespace1" :value "Value1" :category "Category1"})
               tag1-colls)
        tag2 (tags/save-tag
               user2-token
               (tags/make-tag {:namespace "Namespace100" :value "Value2"})
               tag2-colls)
        tag3 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "cmr.namespace2" :value "Value1" :category "Category2"})
               tag3-colls)
        ;; Tag 4 is not associated with any collections
        tag4 (tags/save-tag
               user1-token
               (tags/make-tag {:namespace "Namespace Other" :value "Value Other"}))]
    (index/wait-until-indexed)

    (testing "include-tags in json format has proper tags added to json response."
      (are2 [include-tags dataset-id-tags]
            (let [feed-id (->> (-> include-tags
                                   (str/replace "?" "%3F")
                                   (str/replace "," "%2C"))
                               (format "collections.json?provider=PROV1&include_tags=%s"))
                  expected-json (-> (da/collections->expected-atom all-prov1-colls feed-id)
                                    (update-in [:entries] add-tags-to-collections dataset-id-tags))
                  response (search/find-concepts-json :collection {:provider "PROV1"
                                                                   :include-tags include-tags})
                  {:keys [status results]} response]
              (= [200 expected-json] [status results]))

            "match all tags"
            "*" {"coll1" [["Namespace1" "Value1"] ["Namespace100" "Value2"]]
                 "coll2" [["Namespace100" "Value2"]]
                 "coll3" [["cmr.namespace2" "Value1"]]}

            "match one tag"
            "namespace1" {"coll1" [["Namespace1" "Value1"]]}

            "match tags with wildcard *"
            "namespace1*" {"coll1" [["Namespace1" "Value1"] ["Namespace100" "Value2"]]
                           "coll2" [["Namespace100" "Value2"]]}

            "match tags with wildcard ?"
            "namespace?" {"coll1" [["Namespace1" "Value1"]]}

            "match no tag"
            "namespace3*" {}

            "match empty tag"
            "" {}

            "match multiple tags"
            "namespace1*,cmr.*" {"coll1" [["Namespace1" "Value1"] ["Namespace100" "Value2"]]
                                 "coll2" [["Namespace100" "Value2"]]
                                 "coll3" [["cmr.namespace2" "Value1"]]}))

    (testing "include-tags in collection search with result formats orther than JSON is ignored."
      ;; search in different metadata formats
      (are [metadata-format]
           (d/assert-metadata-results-match
             metadata-format all-prov1-colls
             (search/find-metadata
               :collection metadata-format {:provider "PROV1" :include-tags "*"}))

           :dif
           :dif10
           :echo10
           :iso19115)

      ;; search in xml reference
      (is (d/refs-match? [coll1] (search/find-refs :collection {:tag-namespace "namespace1"
                                                                :provider "PROV1"
                                                                :include-tags "*"})))

      ;; search in atom format
      (let [expected-atom (da/collections->expected-atom
                            all-prov1-colls
                            "collections.atom?provider=PROV1&include_tags=*")
            response (search/find-concepts-atom :collection {:provider "PROV1"
                                                             :include-tags "*"})
            {:keys [status results]} response]
        (is (= [200 expected-atom] [status results]))))

    (testing "include-tags is not supported on granule searches."
      (is (= {:status 400
              :errors ["Parameter [include_tags] was not recognized."]}
             (search/find-refs :granule {:include-tags true}))))))


