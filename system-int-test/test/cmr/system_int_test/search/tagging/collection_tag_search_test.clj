(ns cmr.system-int-test.search.tagging.collection-tag-search-test
  "This tests searching for collections by tag parameters"
  (:require
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are2]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.atom :as atom]
   [cmr.system-int-test.data2.collection :as collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

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
    (are [field option-field]
         (= {:status 400
             :errors [(format "#/condition/tag: extraneous key [%s] is not permitted"
                              (name option-field))]}
            (search/find-refs-with-json-query :collection {} {:tag {field "foo" option-field true}}))
         :tag_key :and
         :tag_key :ignore_case
         :originator_id :and
         :originator_id :ignore_case)))

(deftest search-for-collections-by-tag-params-test
  (let [[c1-p1 c2-p1 c3-p1
         c1-p2 c2-p2 c3-p2] (for [p ["PROV1" "PROV2"]
                                  n (range 1 4)]
                              (data-core/ingest p (collection/collection
                                                   {:entry-title (str "coll" n)})))

        ;; Wait until collections are indexed so tags can be associated with them
        _ (index/wait-until-indexed)

        user1-token (echo-util/login (system/context) "user1")
        user2-token (echo-util/login (system/context) "user2")

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
            (data-core/refs-match? (distinct (apply concat expected-tags-colls))
                                   (search/find-refs :collection query))

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Tag-key Param

            "By tag-key - always case-insensitive"
            [tag1-colls] {:tag-key "tag1"}

            "Tag-key search is pattern search by default"
            [tag3-colls] {:tag-key "*other"}

            "By tag-key Pattern true"
            [tag3-colls] {:tag-key "*other" "options[tag-key][pattern]" true}

            "By tag-key Pattern false"
            [] {:tag-key "*other" "options[tag-key][pattern]" false}

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
            (data-core/refs-match? (distinct (apply concat expected-tags-colls))
                                   (search/find-refs-with-json-query :collection {} query))

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; tag-key query

            "By tag-key - Ignore Case Default"
            [tag1-colls] {:tag {:tag_key "tag1"}}

            "By tag-key Pattern"
            [tag3-colls] {:tag {:tag_key "*other" :pattern true}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; Originator Id Param

            "By Originator Id - Ignore Case Default" ;; Case sensitive not supported
            [tag1-colls tag3-colls] {:tag {:originator_id "USER1"}}

            "By Originator Id - Pattern"
            [tag2-colls] {:tag {:originator_id "*2" :pattern true}}

            ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
            ;; JSON Query Conditional constructs: and, or, not

            "By or'ing tags"
            [tag1-colls tag2-colls tag3-colls] {:or [{:tag {:tag_key "tag*" :pattern true}}
                                                     {:tag {:originator_id "USER2"}}]}

            "By and'ing tags"
            [tag2-colls] {:and [{:tag {:tag_key "tag*" :pattern true}}
                                {:tag {:originator_id "USER2"}}]}

            "By negating ('not') tags"
            [tag1-colls tag2-colls] {:not {:tag {:tag_key "tag other"}}}

            "By nested 'and', 'or', and 'not' tags"
            [tag2-colls] {:and [{:not {:tag {:tag_key "tag other"}}}
                                {:or [{:tag {:tag_key "tag2"}}
                                      {:tag {:tag_key "tag other"}}]}]}))

    (testing "Combination of tag parameters and other collection conditions"
      (is (data-core/refs-match? [c1-p1 c2-p1]
                                 (search/find-refs
                                  :collection
                                  {:tag-key "tag?"
                                   "options[tag-key][pattern]" true
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

(defn- assert-metadata-results-tags-match
  "Assert the metadata search result tags info matches the given collection tags map."
  [coll-tags search-result]
  (let [expected-result (into {}
                              (for [[k v] coll-tags]
                                [(:concept-id k) v]))]
    (is (= expected-result search-result))))

(deftest search-for-collections-with-include-tags-test
  (let [[coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                                    (data-core/ingest "PROV1"
                                                      (collection/collection
                                                       {:entry-title (str "coll" n)})))
        coll5 (data-core/ingest "PROV2" (collection/collection))
        all-prov1-colls [coll1 coll2 coll3 coll4]

        user1-token (echo-util/login (system/context) "user1")
        user2-token (echo-util/login (system/context) "user2")

        tag1-colls [coll1 coll5]
        tag2-colls [coll1 coll2]
        tag3-colls [coll3]

        tag1 (tags/save-tag user1-token (tags/make-tag {:tag-key "tag1"}))
        ;; Wait until collections are indexed so tags can be associated with them
        _ (index/wait-until-indexed)

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

    (tags/associate-by-concept-ids user1-token "tag1" [{:concept-id (:concept-id coll1)
                                                        :revision-id (:revision-id coll1)
                                                        :data "snow"}
                                                       {:concept-id (:concept-id coll5)
                                                        :revision-id (:revision-id coll5)
                                                        :data "<cloud>"}])
    (index/wait-until-indexed)

    (testing "include-tags in atom/json format has proper tags added to atom/json response."
      (are2
        [include-tags dataset-id-tags]
        (let [expected-result
              (fn [response-format]
                (let [expected-fn (if (= :atom response-format)
                                    atom/collections->expected-atom
                                    atom/collections->expected-json)
                      feed-id (->> (-> include-tags
                                       (str/replace "?" "%3F")
                                       (str/replace "," "%2C"))
                                   (format "collections.%s?provider=PROV1&include_tags=%s"
                                           (name response-format)))]
                  (-> (expected-fn all-prov1-colls feed-id)
                      (update-in [:entries] add-tags-to-collections dataset-id-tags))))

              {json-status :status json-results :results}
              (search/find-concepts-json :collection {:provider "PROV1"
                                                      :include-tags include-tags})

              {atom-status :status atom-results :results}
              (search/find-concepts-atom :collection {:provider "PROV1"
                                                      :include-tags include-tags})]

          (and (= [200 (expected-result :json)] [json-status json-results])
               (= [200 (expected-result :atom)] [atom-status atom-results])))

        "include all tags"
        "*" {"coll1" {"tag1" {"data" "snow"} "tag2" {}}
             "coll2" {"tag2" {}}
             "coll3" {"cmr.other" {}}}

        "include one tag"
        "tag1" {"coll1" {"tag1" {"data" "snow"}}}

        "include tags with wildcard *"
        "tag*" {"coll1" {"tag1" {"data" "snow"} "tag2" {}}
                "coll2" {"tag2" {}}
                "coll3" nil}

        "include tags with wildcard ?"
        "tag?" {"coll1" {"tag1" {"data" "snow"} "tag2" {}}
                "coll2" {"tag2" {}}}

        "include no tag"
        "tag3*" {}

        "include empty tag"
        "" {}

        "match multiple tags"
        "tag*,cmr.*" {"coll1" {"tag1" {"data" "snow"} "tag2" {}}
                      "coll2" {"tag2" {}}
                      "coll3" {"cmr.other" {}}}))

    (testing "include-tags in supported metadata format has proper tags added to the response."
      (are [metadata-format]
        (assert-metadata-results-tags-match
         {coll1 {"tag1" {"data" "snow"} "tag2" {}}
          coll2 {"tag2" {}}
          coll3 {"cmr.other" {}}
          coll4 nil
          coll5 {"tag1" {"data" "<cloud>"}}}
         (search/find-metadata-tags :collection metadata-format {:include-tags "*"}))

        :dif
        :dif10
        :echo10
        :native
        :iso19115)

      (are [metadata-format]
        (assert-metadata-results-tags-match
         {coll1 {"tag1" {"data" "snow"}}
          coll2 nil
          coll3 nil
          coll4 nil}
         (search/find-metadata-tags
          :collection metadata-format {:provider "PROV1" :include-tags "tag1"}))

        :dif
        :dif10
        :echo10
        :native
        :iso19115)

      ;; direct transformer query special case
      (are [metadata-format]
        (assert-metadata-results-tags-match
         {coll1 {"tag1" {"data" "snow"} "tag2" {}}}
         (search/find-metadata-tags
          :collection metadata-format {:concept-id (:concept-id coll1) :include-tags "*"}))

        :dif
        :dif10
        :echo10
        :native
        :iso19115))

    (testing "Invalid include-tags params"
      (testing "include-tags in collection search with result formats orther than JSON is invalid."
        (are2 [search-fn format-type]
          (= {:status 400
              :errors [(format "Parameter [include_tags] is not supported for %s format search."
                               (name format-type))]}
             (search-fn :collection {:include-tags "*"}))

          "search in xml reference"
          search/find-refs :xml

          "search in kml format"
          search/find-concepts-kml :kml

          "search in csv format"
          search/find-concepts-csv :csv

          "search in opendata format"
          search/find-concepts-opendata :opendata

          "search in umm-json format"
          search/find-concepts-umm-json :umm-json-results))

      (testing "include-tags is not supported on granule searches."
        (is (= {:status 400
                :errors ["Parameter [include_tags] was not recognized."]}
               (search/find-concepts-json :granule {:include-tags true})))))))

(deftest search-for-collections-with-associated-tag-data-test
  (let [[coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                                    (data-core/ingest "PROV1"
                                                      (collection/collection
                                                       {:entry-title (str "coll" n)})))
        all-prov1-colls [coll1 coll2 coll3 coll4]
        user1-token (echo-util/login (system/context) "user1")
        tag1 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag1"}))
        tag2 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag2"}))
        tag3 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag3"}))]
    (index/wait-until-indexed)

    (tags/associate-by-concept-ids user1-token "tag1" [{:concept-id (:concept-id coll1)
                                                        :data "coll1 tag1 association"}
                                                       {:concept-id (:concept-id coll2)
                                                        :data ["coll2 tag1 association" true]}
                                                       {:concept-id (:concept-id coll3)
                                                        :data {:review_status "reviewed"
                                                               :review-score 85}}])
    (tags/associate-by-concept-ids user1-token "tag2" [{:concept-id (:concept-id coll1)
                                                        :data {:description "coll1 tag2 association"}}
                                                       {:concept-id (:concept-id coll2)
                                                        :data 100}])
    (tags/associate-by-concept-ids user1-token "tag3" [{:concept-id (:concept-id coll3)
                                                        :data "tag 3 rocks"}])
    (index/wait-until-indexed)
    (let [dataset-id-tags {"coll1" {"tag1" {"data" "coll1 tag1 association"}
                                    "tag2" {"data" {"description" "coll1 tag2 association"}}}
                           "coll2" {"tag1" {"data" ["coll2 tag1 association" true]}
                                    "tag2" {"data" 100}}
                           "coll3" {"tag1" {"data" {"review_status" "reviewed"
                                                    "review-score" 85}}
                                    "tag3" {"data" "tag 3 rocks"}}}
          expected-result
          (fn [response-format]
            (let [expected-fn (if (= :atom response-format)
                                atom/collections->expected-atom
                                atom/collections->expected-json)
                  feed-id (format "collections.%s?provider=PROV1&include_tags=*"
                                  (name response-format))]
              (-> (expected-fn all-prov1-colls feed-id)
                  (update-in [:entries] add-tags-to-collections dataset-id-tags))))

          {json-status :status json-results :results}
          (search/find-concepts-json :collection {:provider "PROV1"
                                                  :include-tags "*"})

          {atom-status :status atom-results :results}
          (search/find-concepts-atom :collection {:provider "PROV1"
                                                  :include-tags "*"})]
      (is (= [200 (expected-result :json)] [json-status json-results]))
      (is (= [200 (expected-result :atom)] [atom-status atom-results])))))

(deftest search-collections-by-tag-data-test
  (let [[coll1 coll2 coll3 coll4] (for [n (range 1 5)]
                                    (data-core/ingest "PROV1"
                                                      (collection/collection
                                                       {:entry-title (str "coll" n)})))
        all-prov1-colls [coll1 coll2 coll3 coll4]
        user1-token (echo-util/login (system/context) "user1")
        tag1 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag1"}))
        tag2 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag2"}))
        tag3 (tags/save-tag
               user1-token
               (tags/make-tag {:tag-key "tag3"}))]
    (index/wait-until-indexed)

    (tags/associate-by-concept-ids user1-token "tag1" [{:concept-id (:concept-id coll1)
                                                        :data "cloud"}
                                                       {:concept-id (:concept-id coll2)
                                                        :data "snow"}
                                                       {:concept-id (:concept-id coll3)
                                                        :data {:status "cloud"
                                                               :data "snow"}}])
    (tags/associate-by-concept-ids user1-token "tag2" [{:concept-id (:concept-id coll1)
                                                        :data "snow"}
                                                       {:concept-id (:concept-id coll2)
                                                        :data "cloud"}])
    (tags/associate-by-concept-ids user1-token "tag3" [{:concept-id (:concept-id coll3)
                                                        :data "TAG 3 ROCKS"}])
    (index/wait-until-indexed)

    (testing "search with valid tag-data"
      (are [expected-colls query]
           (data-core/refs-match? expected-colls
                                  (search/find-refs
                                   :collection
                                   query
                                   {:snake-kebab? false}))

           ;; tag-data search is always case-insensitive
           [coll3] {:tag-data {"TAG3" "Tag 3 Rocks"}}
           [coll3] {:tag-data {"tag3" "tag 3 rocks"}}

           ;; tag-data search is grouped by tag-key and tag-value
           [coll1] {:tag-data {"tag1" "cloud"}}
           [coll2] {:tag-data {"tag1" "snow"}}

           ;; tag-data search is not pattern search by default"
           [] {:tag-data {"tag*" "tag*"}}
           [] {:tag-data {"tag*" "tag*"} "options[tag-data][pattern]" false}
           [coll3] {:tag-data {"tag*" "tag*"} "options[tag-data][pattern]" true}
           [] {:tag-data {"*" "snow"}}
           [] {:tag-data {"*" "snow"} "options[tag-data][pattern]" false}
           [coll1 coll2] {:tag-data {"*" "snow"} "options[tag-data][pattern]" true}

           ;; multiple tag-data search is AND together
           [coll1] {:tag-data {"tag1" "cloud" "tag2" "snow"}}
           [] {:tag-data {"tag1" "cloud" "tag2" "cloud"}}))

    (testing "search with invalid tag-data format"
      (are2 [params]
            (= {:status 400
                :errors ["Tag data search must be in the form of tag-data[tag-key]=tag-value"]}
               (search/find-refs :collection params))

            "tag-data=foo is invalid"
            {:tag-data "foo"}

            "tag-data[]=foo is invalid"
            {"tag-data[]" "foo"}))

    (testing "search with empty tag value for tag-data search"
      (is (= {:status 400
              :errors ["Tag value cannot be empty for tag data search, but were for tag keys [foo]."]}
             (search/find-refs :collection {:tag-data {"foo" nil}})))
      (is (= {:status 400
              :errors ["Tag value cannot be empty for tag data search, but were for tag keys [foo, bar]."]}
             (search/find-refs :collection {:tag-data {"foo" nil "x" "y" "bar" ""}}))))

    (testing "Unsupported tag-data options"
      (are [option]
           (= {:status 400
               :errors [(format "Option [%s] is not supported for param [tag_data]" option)]}
              (search/find-refs :collection
                                {:tag_data {"foo" "bar"} (format "options[tag_data][%s]" option) true}))

           "and"
           "ignore_case"))))
