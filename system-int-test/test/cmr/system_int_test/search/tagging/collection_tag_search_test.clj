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
    (are [field option]
         (= {:status 400
             :errors [(format "Option [%s] is not supported for param [%s]" option (name field))]}
            (search/find-refs :collection
                              {field "foo" (format "options[%s][%s]" (name field) option) true}))

         ;; tag-key and originator-id do not support ignore case because they are always case insensitive
         :tag_key "and"
         :tag_key "ignore_case"
         :tag_originator_id "and"
         :tag_originator_id "ignore_case")))

(deftest search-for-collections-with-tag-json-query-validation-test
  (testing "Unsupported options and"
    (are [field]
         (= {:status 400
             :errors ["/condition/tag object instance has properties which are not allowed by the schema: [\"and\"]"]}
            (search/find-refs-with-json-query :collection {} {:tag {field "name" :and true}}))
         :tag_key :originator_id))

  (testing "Unsupported ignore_case option because tag-key and originator-id are always case insensitive"
    (are [field]
         (= {:status 400
             :errors [(format "/condition/tag/%s instance failed to match exactly one schema (matched 0 out of 2)" (name field))
                      (format "/condition/tag/%s instance type (object) does not match any allowed primitive type (allowed: [\"string\"])" (name field))
                      (format "/condition/tag/%s object instance has properties which are not allowed by the schema: [\"ignore_case\"]" (name field))]}
            (search/find-refs-with-json-query :collection {}
                                              {:tag {field {:value "foo"
                                                            :ignore_case true}}}))
         :tag_key :originator_id)))

(deftest search-for-collections-by-tag-params-test
  (let [[c1-p1 c2-p1 c3-p1
         c1-p2 c2-p2 c3-p2] (for [p ["PROV1" "PROV2"]
                                  n (range 1 4)]
                              (d/ingest p (dc/collection {:entry-title (str "coll" n)})))

        ;; Wait until collections are indexed so tags can be associated with them
        _ (index/wait-until-indexed)

        user1-token (e/login (s/context) "user1")
        user2-token (e/login (s/context) "user2")

        tag1-colls [c1-p1 c1-p2]
        tag2-colls [c2-p1 c2-p2]

        tag3-colls [c3-p1 c3-p2]

        tag1 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "Tag1"})
               tag1-colls)
        tag2 (tags/save-tag
               user2-token
               (tags/make-tag {:tag-key "tag2"})
               tag2-colls)

        tag3 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag other"})
               tag3-colls)
        ;; Tag 4 is not associated with any collections
        tag4 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag4"}))]
    (index/wait-until-indexed)

    (testing "All tag parameters with XML references"
      (are2 [expected-tags-colls query]
            (d/refs-match? (distinct (apply concat expected-tags-colls))
                           (search/find-refs :collection query))

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Tag-key Param

            "By tag-key - always case-insensitive"
            [tag1-colls] {:tag-key "tag1"}

            "By tag-key Pattern"
            [tag3-colls] {:tag-key "*other" "options[tag-key][pattern]" true}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Originator Id Param

            "By Originator Id - always case-insensitive"
            [tag1-colls tag3-colls] {:tag-originator-id "USER1"}

            "By Originator Id - Pattern"
            [tag2-colls] {:tag-originator-id "*2" "options[tag-originator-id][pattern]" true}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Exclude tag-key Param
            "Exclude tag-key"
            [tag2-colls tag3-colls] {:exclude {:tag-key "tag1"}}

            "Exclude tag-key - multiple"
            [tag2-colls] {:exclude {:tag-key ["tag1" "tag other"]}}

            "Exclude tag-key Pattern"
            [tag1-colls tag2-colls]
            {:exclude {:tag-key "*other"} "options[tag-key][pattern]" true}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Parameter Combinations
            "Combination with exclude tag-key"
            [tag3-colls] {:tag-key ["Tag1" "tag other"]
                          :exclude {:tag-key "tag1"}}))

    (testing "Search collections by tags with JSON query"
      (are2 [expected-tags-colls query]
            (d/refs-match? (distinct (apply concat expected-tags-colls))
                           (search/find-refs-with-json-query :collection {} query))

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; tag-key query

            "By tag-key - Ignore Case Default"
            [tag1-colls] {:tag {:tag_key "tag1"}}

            "By tag-key Pattern"
            [tag3-colls] {:tag {:tag_key {:value "*other" :pattern true}}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Originator Id Param

            "By Originator Id - Ignore Case Default" ;; Case sensitive not supported
            [tag1-colls tag3-colls] {:tag {:originator_id "USER1"}}

            "By Originator Id - Pattern"
            [tag2-colls] {:tag {:originator_id {:value "*2" :pattern true}}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; JSON Query Conditional constructs: and, or, not

            "By or'ing tags"
            [tag1-colls tag2-colls tag3-colls] {:or [{:tag {:tag_key {:value "tag*" :pattern true}}}
                                                     {:tag {:originator_id "USER2"}}]}

            "By and'ing tags"
            [tag2-colls] {:and [{:tag {:tag_key {:value "tag*" :pattern true}}}
                                {:tag {:originator_id "USER2"}}]}

            "By negating ('not') tags"
            [tag1-colls tag2-colls] {:not {:tag {:tag_key "tag other"}}}

            "By nested 'and', 'or', and 'not' tags"
            [tag2-colls] {:and [{:not {:tag {:tag_key "tag other"}}}
                                {:or [{:tag {:tag_key "tag2"}}
                                      {:tag {:tag_key "tag other"}}]}]}))

    (testing "Combination of tag parameters and other collection conditions"
      (is (d/refs-match? [c1-p1 c2-p1] (search/find-refs :collection {:tag-key "tag?" "options[tag-key][pattern]" true
                                                                      :provider "PROV1"}))))))

(defn- add-tags-to-collections
  "Returns the collections with the tags associated based on the given dataset-id-tags mapping.
  dataset-id-tags is a map of collection dataset-id to tags that should be associated with the
  collection. The tags are in tuples of tag namespace and value which is the same as tags in
  the JSON response. It is used to construct the tags field in the expected JSON."
  [colls dataset-id-tags]
  (map (fn [c]
         (if-let [tags (dataset-id-tags (:dataset-id c))]
           (assoc c :tags tags)
           c))
       colls))

(deftest search-for-collections-with-include-tags-test
  (let [[coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                                    (d/ingest "PROV1" (dc/collection {:entry-title (str "coll" n)})))
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
               (tags/make-tag {:tag-key "Tag1"})
               tag1-colls)
        tag2 (tags/save-tag
               user2-token
               (tags/make-tag {:tag-key "Tag2"})
               tag2-colls)

        tag3 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "cmr.other"})
               tag3-colls)
        ;; Tag 4 is not associated with any collections
        tag4 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag4"}))]
    (index/wait-until-indexed)

    (testing "include-tags in json format has proper tags added to json response."
      (are2
        [include-tags dataset-id-tags]
        (let [expected-result
              (fn [response-format]
                (let [feed-id (->> (-> include-tags
                                       (str/replace "?" "%3F")
                                       (str/replace "," "%2C"))
                                   (format "collections.%s?provider=PROV1&include_tags=%s"
                                           (name response-format)))]
                  (-> (da/collections->expected-atom all-prov1-colls feed-id)
                      (update-in [:entries] add-tags-to-collections dataset-id-tags))))

              {json-status :status json-results :results}
              (search/find-concepts-json :collection {:provider "PROV1"
                                                      :include-tags include-tags})

              {atom-status :status atom-results :results}
              (search/find-concepts-atom :collection {:provider "PROV1"
                                                      :include-tags include-tags})]

          (and (= [200 (expected-result :json)] [json-status json-results])
               (= [200 (expected-result :atom)] [atom-status atom-results])))

        "match all tags"
        "*" {"coll1" [["tag1"] ["tag2"]]
             "coll2" [["tag2"]]
             "coll3" [["cmr.other"]]}

        "match one tag"
        "tag1" {"coll1" [["tag1"]]}

        "match tags with wildcard *"
        "tag*" {"coll1" [["tag1"] ["tag2"]]
                "coll2" [["tag2"]]}

        "match tags with wildcard ?"
        "tag?" {"coll1" [["tag1"] ["tag2"]]
                "coll2" [["tag2"]]}

        "match no tag"
        "tag3*" {}

        "match empty tag"
        "" {}

        "match multiple tags"
        "tag*,cmr.*" {"coll1" [["tag1"] ["tag2"]]
                      "coll2" [["tag2"]]
                      "coll3" [["cmr.other"]]}))

    (testing "Invalid include-tags params"
      (testing "include-tags in collection search with metadata formats orther than JSON is invalid."
        (are [metadata-format]
             (= {:status 400
                 :errors ["Parameter [include_tags] is only supported in ATOM or JSON format search."]}
                (search/find-metadata
                  :collection metadata-format {:provider "PROV1" :include-tags "*"}))

             :dif
             :dif10
             :echo10
             :iso19115))

      (testing "include-tags in collection search with result formats orther than JSON is invalid."
        (are2 [search-fn]
              (= {:status 400
                  :errors ["Parameter [include_tags] is only supported in ATOM or JSON format search."]}
                 (search-fn :collection {:include-tags "*"}))

              "search in xml reference"
              search/find-refs

              "search in kml format"
              search/find-concepts-kml

              "search in csv format"
              search/find-concepts-csv

              "search in opendata format"
              search/find-concepts-opendata

              "search in umm-json format"
              search/find-concepts-umm-json))

      (testing "include-tags is not supported on granule searches."
        (is (= {:status 400
                :errors ["Parameter [include_tags] was not recognized."]}
               (search/find-concepts-json :granule {:include-tags true})))))))


