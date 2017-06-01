(ns cmr.system-int-test.search.tagging.collection-revisions-tag-search-test
  "This tests searching for collection revisions by tag parameters"
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                       tags/grant-all-tag-fixture]))

(defn- assert-collection-refs-found
  "Assert collection references are found by searching with the given params, including tag-data"
  [expected-colls params]
  (d/assert-refs-match expected-colls
                       (search/find-refs :collection params {:snake-kebab? false})))

(deftest search-collection-revisions-by-tag-test
  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge (ingest/delete-concept concept1) concept1 {:deleted true})
        coll1-3 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))

        coll2-1 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        coll2-2 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))

        coll3 (d/ingest "PROV2" (dc/collection {}))
        coll4-1 (d/ingest "PROV2" (dc/collection {:entry-title "et4"}))

        token (e/login (s/context) "user1")
        tag1 (tags/save-tag token (tags/make-tag {:tag-key "tag1"}))
        tag2 (tags/save-tag token (tags/make-tag {:tag-key "tag2"}))
        tag3 (tags/save-tag token (tags/make-tag {:tag-key "tag3"}))]
    (index/wait-until-indexed)

    (tags/associate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll1-1)
                                                  :revision-id (:revision-id coll1-1)
                                                  :data "snow"}
                                                 {:concept-id (:concept-id coll1-3)
                                                  :revision-id (:revision-id coll1-3)
                                                  :data "snow"}
                                                 {:concept-id (:concept-id coll2-1)
                                                  :revision-id (:revision-id coll2-1)
                                                  :data "cloud"}
                                                 {:concept-id (:concept-id coll3)
                                                  :data {:status "cloud"
                                                         :data "snow"}}])
    (tags/associate-by-concept-ids token "tag2" [{:concept-id (:concept-id coll2-1)
                                                  :revision-id (:revision-id coll2-1)
                                                  :data "snow"}
                                                 {:concept-id (:concept-id coll2-2)
                                                  :revision-id (:revision-id coll2-2)
                                                  :data "cloud"}
                                                 {:concept-id (:concept-id coll4-1)
                                                  :data "land"}])
    (tags/associate-by-concept-ids token "tag3" [{:concept-id (:concept-id coll3)
                                                  :revision-id (:revision-id coll3)
                                                  :data "TAG 3 ROCKS"}])

    (testing "search with tag-data"
      (are [expected-colls params]
           (assert-collection-refs-found expected-colls params)

           ;; the latest revision is always found no matter if we are searching with all-revisions
           [coll3] {:tag-data {"tag3" "tag 3 rocks"} :all-revisions true}
           [coll3] {:tag-data {"tag3" "tag 3 rocks"}}

           ; ;; revisions that are not the latest are only found when searching all-revisions true
           [coll1-1 coll1-3] {:tag-data {"tag1" "snow"} :all-revisions true}
           [coll1-3] {:tag-data {"tag1" "snow"}}
           [coll2-1] {:tag-data {"tag2" "snow"} :all-revisions true}
           [] {:tag-data {"tag2" "snow"}}))

    (testing "update collection with collection level tag"
      ;; collection revisions are found
      (assert-collection-refs-found [coll4-1] {:tag-data {"tag2" "land"}})
      (assert-collection-refs-found [coll4-1] {:tag-data {"tag2" "land"} :all-revisions true})

      ;; update the collection
      (let [coll4-2 (d/ingest "PROV2" (dc/collection {:entry-title "et4"}))]
        (index/wait-until-indexed)

        ;; Found the latest collection revision only without all-revisions true
        (assert-collection-refs-found [coll4-2] {:tag-data {"tag2" "land"}})
        ;; Found all revisions with all revisions true
        (assert-collection-refs-found [coll4-1 coll4-2] {:tag-data {"tag2" "land"}
                                                         :all-revisions true})))

    (testing "update collection with collection revision level tag"
      ;; collection revisions are found
      (assert-collection-refs-found [coll2-2] {:tag-data {"tag2" "cloud"}})
      (assert-collection-refs-found [coll2-2] {:tag-data {"tag2" "cloud"} :all-revisions true})

      ;; update the collection
      (let [coll2-3 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))]
        (index/wait-until-indexed)

        ;; Found no collections without all-revisions true
        (assert-collection-refs-found [] {:tag-data {"tag2" "cloud"}})
        ;; Found only the revisions tagged with all revisions true
        (assert-collection-refs-found [coll2-2] {:tag-data {"tag2" "cloud"}
                                                 :all-revisions true})))

    (testing "tag a collection revision again with different data"
      ;; The state before collection revision is tagged again
      (assert-collection-refs-found [coll1-3] {:tag-data {"tag1" "snow"}})
      (assert-collection-refs-found [coll1-1 coll1-3] {:tag-data {"tag1" "snow"} :all-revisions true})
      ;; nothing with ice
      (assert-collection-refs-found [] {:tag-data {"tag1" "ice"}})
      (assert-collection-refs-found [] {:tag-data {"tag1" "ice"} :all-revisions true})

      ;; tag collection revision with different data
      (tags/associate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll1-1)
                                                    :revision-id (:revision-id coll1-1)
                                                    :data "ice"}])

      ;; The state after collection revision is tagged again
      ;; search without all-revision true found the same result
      (assert-collection-refs-found [coll1-3] {:tag-data {"tag1" "snow"}})
      (assert-collection-refs-found [] {:tag-data {"tag1" "ice"}})
      ;; search with all-revisions true find different result due to the new tag associated
      (assert-collection-refs-found [coll1-3] {:tag-data {"tag1" "snow"} :all-revisions true})
      (assert-collection-refs-found [coll1-1] {:tag-data {"tag1" "ice"} :all-revisions true}))

    (testing "tag dissociation"
      (tags/associate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll2-1)
                                                    :revision-id (:revision-id coll2-1)
                                                    :data "ice"}
                                                   {:concept-id (:concept-id coll2-2)
                                                    :revision-id (:revision-id coll2-2)
                                                    :data "ice"}])
      ;; before tag dissociation
      (assert-collection-refs-found [coll1-3] {:tag-data {"tag1" "snow"}})
      (assert-collection-refs-found [coll1-1 coll2-1 coll2-2]
                                    {:tag-data {"tag1" "ice"} :all-revisions true})

      ;; dissociate tag
      (tags/dissociate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll2-1)
                                                       :revision-id (:revision-id coll2-1)}
                                                      {:concept-id (:concept-id coll1-3)
                                                       :revision-id (:revision-id coll1-3)}])
      ;; after tag dissociation
      (assert-collection-refs-found [] {:tag-data {"tag1" "snow"}})
      (assert-collection-refs-found [coll1-1 coll2-2]
                                    {:tag-data {"tag1" "ice"} :all-revisions true}))))
