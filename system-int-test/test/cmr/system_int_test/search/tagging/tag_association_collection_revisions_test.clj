(ns cmr.system-int-test.search.tagging.tag-association-collection-revisions-test
  "This tests associating tags with collection revisions."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are2] :as util]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                                             {:grant-all-search? false})
                       tags/grant-all-tag-fixture]))

(defn- assert-tag-association
  "Assert the collections are associated with the tag for the given tag-key.
  If the options has :all-revisions true, the collections will be search on all collection revisions."
  ([token colls tag-key]
   (assert-tag-association token colls tag-key {}))
  ([token colls tag-key options]
   (is (d/refs-match?
         colls
         (search/find-refs :collection (merge {:token token :tag-key tag-key} options))))))

(deftest tag-association-collection-revisions-test
  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV2"))

  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge (ingest/delete-concept concept1) concept1 {:deleted true})
        coll1-3 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))

        coll2-1 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        coll2-2 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll2-2)}
        coll2-3-tombstone (merge (ingest/delete-concept concept2) concept2 {:deleted true})

        coll3 (d/ingest "PROV2" (dc/collection {}))
        coll4 (d/ingest "PROV3" (dc/collection {}))
        token (e/login (s/context) "user1")
        tag-key "tag1"]

    (tags/create-tag token (tags/make-tag {:tag-key tag-key}))
    (index/wait-until-indexed)

    (testing "successful case, the tag association keys can have either _ or -"
      (let [response (tags/associate-by-concept-ids
                      token tag-key [{:concept_id (:concept-id coll1-1)
                                      :revision_id (:revision-id coll1-1)
                                      :data "snow"}
                                     {:concept-id (:concept-id coll3)
                                      :data "cloud"}])]
        (index/wait-until-indexed)
        (tags/assert-tag-association-response-ok?
         {[(:concept-id coll1-1) (:revision-id coll1-1)] {:concept-id "TA1200000005-CMR"
                                                          :revision-id 1}
          [(:concept-id coll3)] {:concept-id "TA1200000006-CMR"
                                 :revision-id 1}}
         response)))

    (testing "revision-id must be an integer"
      (let [{:keys [status errors]} (tags/associate-by-concept-ids
                                     token tag-key
                                     [{:concept-id (:concept-id coll1-1)
                                       :revision-id "1"}])
            expected-msg "#/0/revision_id: expected type: Integer, found: String"]
        (is (= [400 [expected-msg]] [status errors]))))

    (testing "tag a non-existent collection revision"
      (let [concept-id (:concept-id coll1-1)
            response (tags/associate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id 5}])]
        (tags/assert-tag-association-response-error?
         {[concept-id 5]
          {:errors [(format "Collection with concept id [%s] revision id [5] does not exist or is not visible."
                            concept-id)]}}
         response)))

    (testing "tag an invisible collection revision"
      (let [concept-id (:concept-id coll4)
            revision-id (:revision-id coll4)
            response (tags/associate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id revision-id}])]
        (tags/assert-tag-association-response-error?
         {[concept-id revision-id]
          {:errors [(format "Collection with concept id [%s] revision id [%s] does not exist or is not visible."
                            concept-id revision-id)]}}
         response)))

    (testing "tag a tombstoned revision is invalid"
      (let [concept-id (:concept-id coll1-2-tombstone)
            revision-id (:revision-id coll1-2-tombstone)
            response (tags/associate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id revision-id}])
            expected-msg (format (str "Collection with concept id [%s] revision id [%s] is a "
                                      "tombstone. We don't allow tag association with individual "
                                      "collection revisions that are tombstones.")
                                 concept-id revision-id)]
        (tags/assert-tag-association-response-error?
         {[concept-id revision-id] {:errors [expected-msg]}}
         response)))

    (testing "Cannot tag collection that already has collection revision tagging"
      (let [response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id (:concept-id coll1-3)}])
            expected-msg (format
                          (str "There are already tag associations with tag key [%s] on "
                               "collection [%s] revision ids [%s], cannot create tag association "
                               "on the same collection without revision id.")
                          tag-key (:concept-id coll1-1) (:revision-id coll1-1))]
        (tags/assert-tag-association-response-error?
         {[(:concept-id coll1-3)] {:errors [expected-msg]}}
         response)))

    (testing "Cannot tag collection revision that already has collection tagging"
      (let [concept-id (:concept-id coll3)
            revision-id (:revision-id coll3)
            response (tags/associate-by-concept-ids
                      token tag-key
                      [{:concept-id (:concept-id coll3)
                        :revision-id (:revision-id coll3)}])
            expected-msg (format
                          (str "There are already tag associations with tag key [%s] on "
                               "collection [%s] without revision id, cannot create tag "
                               "association on the same collection with revision id [%s].")
                          tag-key concept-id revision-id)]
        (tags/assert-tag-association-response-error?
         {[concept-id revision-id] {:errors [expected-msg]}}
         response)))

    (testing "tag collection revisions mixed response"
      (let [concept-id (:concept-id coll1-1)
            revision-id (:revision-id coll1-1)
            response (tags/associate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id :revision-id 5}
                       {:concept-id concept-id :revision-id revision-id}])]
        (tags/assert-tag-association-response-error?
         {[concept-id 5]
          {:errors [(format "Collection with concept id [%s] revision id [5] does not exist or is not visible."
                            concept-id)]}
          [concept-id revision-id] {:concept-id "TA1200000005-CMR" :revision-id 2}}
         response)))))

(deftest tag-dissociation-collection-revisions-test
  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV2"))

  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge (ingest/delete-concept concept1) concept1 {:deleted true})
        coll1-3 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))

        coll2-1 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        coll2-2 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll2-2)}
        coll2-3-tombstone (merge (ingest/delete-concept concept2) concept2 {:deleted true})

        coll3 (d/ingest "PROV2" (dc/collection {}))
        coll4 (d/ingest "PROV3" (dc/collection {}))
        token (e/login (s/context) "user1")
        tag-key "tag1"]

    (tags/create-tag token (tags/make-tag {:tag-key tag-key}))
    (index/wait-until-indexed)
    (tags/associate-by-concept-ids
     token tag-key [{:concept-id (:concept-id coll1-1)
                     :revision-id (:revision-id coll1-1)}
                    {:concept-id (:concept-id coll1-3)
                     :revision-id (:revision-id coll1-3)}
                    {:concept-id (:concept-id coll2-1)
                     :revision-id (:revision-id coll2-1)
                     :data "snow"}
                    {:concept-id (:concept-id coll2-2)
                     :revision-id (:revision-id coll2-2)
                     :data "cloud"}
                    {:concept-id (:concept-id coll3)}])
    (index/wait-until-indexed)

    (testing "successful case"
      (let [response (tags/dissociate-by-concept-ids
                      token tag-key [{:concept-id (:concept-id coll2-1)
                                      :revision-id (:revision-id coll2-1)}
                                     {:concept-id (:concept-id coll3)}])]
        (index/wait-until-indexed)
        (tags/assert-tag-dissociation-response-ok?
         {[(:concept-id coll2-1) (:revision-id coll2-1)] {:concept-id "TA1200000007-CMR"
                                                          :revision-id 2}
          [(:concept-id coll3)] {:concept-id "TA1200000009-CMR"
                                 :revision-id 2}}
         response)))

    (testing "revision-id must be an integer"
      (let [{:keys [status errors]} (tags/dissociate-by-concept-ids
                                     token tag-key
                                     [{:concept-id (:concept-id coll1-1)
                                       :revision-id "1"}])
            expected-msg "#/0/revision_id: expected type: Integer, found: String"]
        (is (= [400 [expected-msg]] [status errors]))))

    (testing "dissociate tag of a non-existent collection revision"
      (let [concept-id (:concept-id coll1-1)
            response (tags/dissociate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id 5}])]
        (tags/assert-tag-dissociation-response-error?
         {[concept-id 5]
          {:errors [(format "Collection with concept id [%s] revision id [5] does not exist or is not visible."
                            concept-id)]}}
         response)))

    (testing "dissociate tag of an invisible collection revision"
      (let [concept-id (:concept-id coll4)
            revision-id (:revision-id coll4)
            response (tags/dissociate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id revision-id}])]
        (tags/assert-tag-dissociation-response-error?
         {[concept-id revision-id]
          {:errors [(format "Collection with concept id [%s] revision id [%s] does not exist or is not visible."
                            concept-id revision-id)]}}
         response)))

    (testing "dissociate tag of a tombstoned revision is invalid"
      (let [concept-id (:concept-id coll1-2-tombstone)
            revision-id (:revision-id coll1-2-tombstone)
            response (tags/dissociate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id revision-id}])
            expected-msg (format (str "Collection with concept id [%s] revision id [%s] is a "
                                      "tombstone. We don't allow tag association with individual "
                                      "collection revisions that are tombstones.")
                                 concept-id revision-id)]
        (tags/assert-tag-dissociation-response-error?
         {[concept-id revision-id] {:errors [expected-msg]}}
         response)))

    (testing "dissociate tag of collection that already has collection revision tagging"
      (let [response (tags/dissociate-by-concept-ids
                      token tag-key [{:concept-id (:concept-id coll1-3)}])
            expected-msg (format "Tag [%s] is not associated with collection [%s]."
                                 tag-key (:concept-id coll1-3))]
        (tags/assert-tag-dissociation-response-ok?
         {[(:concept-id coll1-3)] {:warnings [expected-msg]}}
         response)))

    (testing "dissociate tag of individual collection revision that already has been tagged at the collection level"
      (let [concept-id (:concept-id coll3)
            revision-id (:revision-id coll3)
            response (tags/dissociate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id
                        :revision-id revision-id}])
            expected-msg (format (str "Tag [%s] is not associated with the specific collection concept revision "
                                      "concept id [%s] and revision id [%s].")
                                 tag-key concept-id revision-id)]
        (tags/assert-tag-dissociation-response-ok?
         {[concept-id revision-id] {:warnings [expected-msg]}}
         response)))

    (testing "dissociate tag of collection revisions mixed response"
      (let [concept-id (:concept-id coll1-1)
            revision-id (:revision-id coll1-1)
            response (tags/dissociate-by-concept-ids
                      token tag-key
                      [{:concept-id concept-id :revision-id 5}
                       {:concept-id concept-id :revision-id revision-id}])]
        (tags/assert-tag-dissociation-response-error?
         {[concept-id 5]
          {:errors [(format "Collection with concept id [%s] revision id [5] does not exist or is not visible."
                            concept-id)]}
          [concept-id revision-id] {:concept-id "TA1200000005-CMR" :revision-id 2}}
         response)))))

(deftest associate-dissociate-tag-with-collection-revisions-test
  ;; Grant all collections in PROV1
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [coll1-1 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        coll1-2 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        coll1-3 (d/ingest "PROV1" (dc/collection {:entry-title "et1"}))
        coll2-1 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        coll2-2 (d/ingest "PROV1" (dc/collection {:entry-title "et2"}))
        token (e/login (s/context) "user1")]
    (tags/create-tag token (tags/make-tag {:tag-key "tag1"}))
    (tags/create-tag token (tags/make-tag {:tag-key "tag2"}))
    (index/wait-until-indexed)

    ;; associate tag1 to coll1-1, coll1-2, coll2-2
    (tags/associate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll1-1)
                                                  :revision-id (:revision-id coll1-1)}
                                                 {:concept-id (:concept-id coll1-2)
                                                  :revision-id (:revision-id coll1-2)}
                                                 {:concept-id (:concept-id coll2-2)
                                                  :revision-id (:revision-id coll2-2)}])
    ;; associate tag2 to coll1-3, coll2-1
    (tags/associate-by-concept-ids token "tag2" [{:concept-id (:concept-id coll1-3)
                                                  :revision-id (:revision-id coll1-3)}
                                                 {:concept-id (:concept-id coll2-1)
                                                  :revision-id (:revision-id coll2-1)}])
    (index/wait-until-indexed)
    ;; verify association, latest revision
    (assert-tag-association token [coll2-2] "tag1")
    (assert-tag-association token [coll1-3] "tag2")
    ;; verify association, all revisions
    (assert-tag-association token [coll1-1 coll1-2 coll2-2] "tag1" {:all-revisions true})
    (assert-tag-association token [coll1-3 coll2-1] "tag2" {:all-revisions true})

    ;; associate tag1 to coll1-1 again, also coll1-3
    (tags/associate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll1-3)
                                                  :revision-id (:revision-id coll1-3)}
                                                 {:concept-id (:concept-id coll1-1)
                                                  :revision-id (:revision-id coll1-1)}])
    (index/wait-until-indexed)
    ;; verify association, latest revision
    (assert-tag-association token [coll1-3 coll2-2] "tag1")
    (assert-tag-association token [coll1-3] "tag2")
    ;; verify association, all revisions
    (assert-tag-association token [coll1-1 coll1-2 coll1-3 coll2-2] "tag1" {:all-revisions true})
    (assert-tag-association token [coll1-3 coll2-1] "tag2" {:all-revisions true})

    ;; dissociate tag1 from coll1-2 and coll2-2
    (tags/dissociate-by-concept-ids token "tag1" [{:concept-id (:concept-id coll1-2)
                                                     :revision-id (:revision-id coll1-2)}
                                                    {:concept-id (:concept-id coll2-2)
                                                     :revision-id (:revision-id coll2-2)}])
    ;; dissociate tag2 from coll1-3
    (tags/dissociate-by-concept-ids token "tag2" [{:concept-id (:concept-id coll1-3)
                                                     :revision-id (:revision-id coll1-3)}])
    (index/wait-until-indexed)
    ;; verify association, latest revision
    (assert-tag-association token [coll1-3] "tag1")
    (assert-tag-association token [] "tag2")
    ;; verify association, all revisions
    (assert-tag-association token [coll1-1 coll1-3] "tag1" {:all-revisions true})
    (assert-tag-association token [coll2-1] "tag2" {:all-revisions true})))
