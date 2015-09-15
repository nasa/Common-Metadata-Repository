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
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

;; TODO test with various formats that will go through the different query execution paths
;; What should those do in that case?

(deftest search-for-collections-by-tag-params-test
  (let [[c1-p1 c2-p1 c3-p1 c4-p1 c5-p1
         c1-p2 c2-p2 c3-p2 c4-p2 c5-p2] (for [p ["PROV1" "PROV2"]
                                              n (range 1 6)]
                                          (d/ingest p (dc/collection {:entry-title (str "coll" n )})))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-colls (concat all-prov1-colls all-prov2-colls)

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



    ))


;; TODO test that if a collection is updated it remains associated with the tag

;; Collection deleted and then reingested is still associated with a tag
;; - test this by search for collections by that tag


;; Other TODOs (maybe in other namespaces)

;; Deleting a tag dissociates the collections
;; - test this by searching for collection by that tag

;; Updating a tag maintains associations
;; - test this by search for collections by that tag

