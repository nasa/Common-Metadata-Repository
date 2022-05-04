(ns cmr.system-int-test.search.tagging.tag-association-test
  "This tests associating tags with collections."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are2] :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.collection :as collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]
   [cmr.transmit.tag :as transmit-tag]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                                            {:grant-all-search? false})
                      tags/grant-all-tag-fixture]))

(deftest associate-tags-by-query-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV2"))

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (for [p ["PROV1" "PROV2" "PROV3"]
                                        n (range 1 5)]
                                    (:concept-id (data-core/ingest
                                                  p
                                                  (collection/collection
                                                   {:short-name (str "S" n)
                                                    :version-id (str "V" n)
                                                    :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        tag (tags/make-tag)
        tag-key (:tag-key tag)
        token (echo-util/login (system/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (index/wait-until-indexed)

    (testing "Successfully Associate tag with collections"
      (let [response (tags/associate-by-query token tag-key {:provider "PROV1"})]
        (tags/assert-tag-association-response-ok?
         {["C1200000013-PROV1"] {:concept-id "TA1200000026-CMR"
                                 :revision-id 1}
          ["C1200000014-PROV1"] {:concept-id "TA1200000027-CMR"
                                 :revision-id 1}
          ["C1200000015-PROV1"] {:concept-id "TA1200000028-CMR"
                                 :revision-id 1}
          ["C1200000016-PROV1"] {:concept-id "TA1200000029-CMR"
                                 :revision-id 1}}
         response)))

    (testing "Associate using query that finds nothing"
      (let [response (tags/associate-by-query token tag-key {:provider "foo"})]
        (tags/assert-tag-association-response-ok? {} response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (tags/associate-by-query token tag-key {:provider "PROV3"})]
        (tags/assert-tag-association-response-ok? {} response)))

    (testing "Associate more collections"
      ;; Associates all the version 2 collections which is c2-p1 (already in) and c2-p2 (new)
      (let [response (tags/associate-by-query token tag-key {:version "v2"})]
        (tags/assert-tag-association-response-ok?
         {["C1200000014-PROV1"] {:concept-id "TA1200000027-CMR"
                                 :revision-id 2}
          ["C1200000018-PROV2"] {:concept-id "TA1200000030-CMR"
                                 :revision-id 1}}
         response)))))

(deftest associate-tags-by-concept-ids-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV2"))

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (for [p ["PROV1" "PROV2" "PROV3"]
                                        n (range 1 5)]
                                    (:concept-id (data-core/ingest
                                                  p
                                                  (collection/collection
                                                   {:short-name (str "S" n)
                                                    :version-id (str "V" n)
                                                    :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (echo-util/login (system/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (index/wait-until-indexed)

    (testing "Associate tag with collections by concept-ids"
      (let [response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id c1-p1}
                                     {:concept-id c3-p2}])]
        (tags/assert-tag-association-response-ok?
         {["C1200000013-PROV1"] {:concept-id "TA1200000026-CMR"
                                 :revision-id 1}
          ["C1200000019-PROV2"] {:concept-id "TA1200000027-CMR"
                                 :revision-id 1}}
         response)))

    (testing "Associate to no collections"
      (let [response (tags/associate-by-concept-ids token tag-key [])]
        (tags/assert-invalid-data-error
         ["At least one collection must be provided for tag association."]
         response)))

    (testing "Associate to collection revision and whole collection at the same time"
      (let [response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id c1-p1}
                                     {:concept-id c1-p1 :revision-id 1}])]
        (tags/assert-invalid-data-error
         [(format (str "Unable to create tag association on a collection revision and the whole "
                       "collection at the same time for the following collections: %s.")
                  c1-p1)]
         response)))

    (testing "Associate to non-existent collections"
      (let [response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id "C100-P5"}])]
        (tags/assert-tag-association-response-error?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Associate to deleted collections"
      (let [c1-p1-concept (mdb/get-concept c1-p1)
            _ (ingest/delete-concept c1-p1-concept)
            _ (index/wait-until-indexed)
            response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id c1-p1}])]
        (tags/assert-tag-association-response-error?
         {[c1-p1] {:errors [(format "Collection [%s] does not exist or is not visible." c1-p1)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (tags/associate-by-concept-ids token tag-key [{:concept-id c4-p3}])]
        (tags/assert-tag-association-response-error?
         {[c4-p3] {:errors [(format "Collection [%s] does not exist or is not visible." c4-p3)]}}
         response)))

    (testing "Tag association mixed response"
      (let [response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id c2-p1}
                                     {:concept-id "C100-P5"}])]
        (tags/assert-tag-association-response-error?
         {["C1200000014-PROV1"] {:concept-id "TA1200000028-CMR"
                                 :revision-id 1}
          ["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))))

(deftest associate-tag-failure-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (let [tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (echo-util/login (system/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        coll-concept-id (:concept-id (data-core/ingest
                                      "PROV1"
                                      (collection/collection)))]
    (testing "Associate tag using query sent with invalid content type"
      (are [associate-tag-fn request-json]
           (= {:status 400
               :errors
               ["The mime types specified in the content-type header [application/xml] are not supported."]}
              (associate-tag-fn token tag-key request-json {:http-options {:content-type :xml}}))
        tags/associate-by-query {:provider "foo"}
        tags/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate applies JSON Query validations"
      (are [associate-tag-fn request-json message]
           (= {:status 400
               :errors [message]}
              (associate-tag-fn token tag-key {:foo "bar"}))

        tags/associate-by-query {:foo "bar"}
        "#/condition: extraneous key [foo] is not permitted"

        tags/associate-by-concept-ids {:concept-id coll-concept-id}
        "#: expected type: JSONArray, found: JSONObject"))

    (testing "Associate tag that doesn't exist"
      (are [associate-tag-fn request-json]
           (= {:status 404
               :errors ["Tag could not be found with tag-key [tag100]"]}
              (associate-tag-fn token "tag100" request-json))
        tags/associate-by-query {:provider "foo"}
        tags/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate deleted tag"
      (tags/delete-tag token tag-key)
      (are [associate-tag-fn request-json]
           (= {:status 404
               :errors [(format "Tag with tag-key [%s] was deleted." tag-key)]}
              (associate-tag-fn token tag-key request-json))
        tags/associate-by-query {:provider "foo"}
        tags/associate-by-concept-ids [{:concept-id coll-concept-id}]))))

(deftest dissociate-tags-with-collections-by-query-test
  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [group1-concept-id (echo-util/get-or-create-group (system/context) "group1")
        ;; Grant all collections in PROV1 and 2
        _ (echo-util/grant-registered-users (system/context)
                                            (echo-util/coll-catalog-item-id "PROV1"))
        _ (echo-util/grant-registered-users (system/context)
                                            (echo-util/coll-catalog-item-id "PROV2"))
        _ (echo-util/grant-group (system/context)
                                 group1-concept-id
                                 (echo-util/coll-catalog-item-id "PROV3"))

        [c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (for [p ["PROV1" "PROV2" "PROV3"]
                                        n (range 1 5)]
                                    (data-core/ingest
                                     p
                                     (collection/collection
                                      {:short-name (str "S" n)
                                       :version-id (str "V" n)
                                       :entry-title (str "ET" n)})))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (echo-util/login (system/context) "user1")
        prov3-token (echo-util/login (system/context)
                                     "prov3-user"
                                     [group1-concept-id])
        {:keys [concept-id]} (tags/create-tag token tag)
        assert-tag-associated (partial tags/assert-tag-associated-with-query
                                       prov3-token {:tag-key "tag1"})]
    (index/wait-until-indexed)
    ;; Associate the tag with every collection
    (tags/associate-by-query prov3-token tag-key {:or [{:provider "PROV1"}
                                                       {:provider "PROV2"}
                                                       {:provider "PROV3"}]})

    (testing "Dissociate using query that finds nothing"
      (let [{:keys [status]} (tags/dissociate-by-query token tag-key {:provider "foo"})]
        (is (= 200 status))
        (assert-tag-associated all-colls)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible to normal users
      (let [{:keys [status]} (tags/dissociate-by-query token tag-key {:provider "PROV3"})]
        (is (= 200 status))
        (assert-tag-associated all-colls)))

    (testing "Successfully dissociate tag with collections"
      (let [{:keys [status]} (tags/dissociate-by-query token tag-key {:provider "PROV1"})]
        (is (= 200 status))
        (assert-tag-associated (concat all-prov2-colls all-prov3-colls)))

      ;; dissociate tag again is OK. Since there is no existing tag association, it does nothing.
      (let [{:keys [status]} (tags/dissociate-by-query token tag-key {:provider "PROV1"})]
        (is (= 200 status))
        (assert-tag-associated (concat all-prov2-colls all-prov3-colls))))))

(deftest dissociate-tags-with-collections-by-concept-ids-test
  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [group1-concept-id (echo-util/get-or-create-group (system/context) "group1")
        ;; Grant all collections in PROV1 and 2
        _ (echo-util/grant-registered-users (system/context)
                                            (echo-util/coll-catalog-item-id "PROV1"))
        _ (echo-util/grant-registered-users (system/context)
                                            (echo-util/coll-catalog-item-id "PROV2"))
        _ (echo-util/grant-group (system/context)
                                 group1-concept-id
                                 (echo-util/coll-catalog-item-id "PROV3"))
        [c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (for [p ["PROV1" "PROV2" "PROV3"]
                                        n (range 1 5)]
                                    (data-core/ingest
                                     p
                                     (collection/collection
                                      {:short-name (str "S" n)
                                       :version-id (str "V" n)
                                       :entry-title (str "ET" n)})))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (echo-util/login (system/context) "user1")
        prov3-token (echo-util/login (system/context)
                                     "prov3-user"
                                     [group1-concept-id])
        {:keys [concept-id]} (tags/create-tag token tag)
        assert-tag-associated (partial tags/assert-tag-associated-with-query
                                       prov3-token {:tag-key "tag1"})]
    (index/wait-until-indexed)
    ;; Associate the tag with every collection
    (tags/associate-by-query prov3-token tag-key {:or [{:provider "PROV1"}
                                                       {:provider "PROV2"}
                                                       {:provider "PROV3"}]})

    (testing "Successfully dissociate tag with collections"
      (let [{:keys [status]} (tags/dissociate-by-concept-ids
                              token
                              tag-key
                              (map #(hash-map :concept-id (:concept-id %)) all-prov1-colls))]
        (is (= 200 status))
        (assert-tag-associated (concat all-prov2-colls all-prov3-colls))))

    (testing "Dissociate non-existent collections"
      (let [response (tags/dissociate-by-concept-ids
                      token tag-key [{:concept-id "C100-P5"}])]
        (tags/assert-tag-dissociation-response-error?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Dissociate to deleted collections"
      (let [c1-p2-concept-id (:concept-id c1-p2)
            c1-p2-concept (mdb/get-concept c1-p2-concept-id)
            _ (ingest/delete-concept c1-p2-concept)
            _ (index/wait-until-indexed)
            response (tags/dissociate-by-concept-ids
                      token tag-key [{:concept-id c1-p2-concept-id}])]
        (tags/assert-tag-dissociation-response-error?
         {["C1200000019-PROV2"] {:errors [(format "Collection [%s] does not exist or is not visible."
                                                  c1-p2-concept-id)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [coll-concept-id (:concept-id c4-p3)
            response (tags/dissociate-by-concept-ids
                      token tag-key [{:concept-id coll-concept-id}])]
        (tags/assert-tag-dissociation-response-error?
         {["C1200000026-PROV3"] {:errors [(format "Collection [%s] does not exist or is not visible."
                                                  coll-concept-id)]}}
         response)))))

(deftest dissociate-tag-failure-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (let [tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (echo-util/login (system/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        coll-concept-id (:concept-id (data-core/ingest
                                      "PROV1"
                                      (collection/collection)))]

    (testing "Dissociate tag using query sent with invalid content type"
      (are [dissociate-tag-fn request-json]
           (= {:status 400
               :errors
               ["The mime types specified in the content-type header [application/xml] are not supported."]}
              (dissociate-tag-fn token tag-key request-json {:http-options {:content-type :xml}}))
        tags/dissociate-by-query {:provider "foo"}
        tags/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate applies JSON Query validations"
      (are [dissociate-tag-fn request-json message]
           (= {:status 400
               :errors [message]}
              (dissociate-tag-fn token tag-key request-json))
        tags/dissociate-by-query {:foo "bar"}
        "#/condition: extraneous key [foo] is not permitted"

        tags/dissociate-by-concept-ids {:concept-id coll-concept-id}
        "#: expected type: JSONArray, found: JSONObject"))

    (testing "Dissociate tag that doesn't exist"
      (are [dissociate-tag-fn request-json]
           (= {:status 404
               :errors ["Tag could not be found with tag-key [tag100]"]}
              (dissociate-tag-fn token "tag100" request-json))
        tags/dissociate-by-query {:provider "foo"}
        tags/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate deleted tag"
      (tags/delete-tag token tag-key)
      (are [dissociate-tag-fn request-json]
           (= {:status 404
               :errors [(format "Tag with tag-key [%s] was deleted." tag-key)]}
              (dissociate-tag-fn token tag-key request-json))
        tags/dissociate-by-query {:provider "foo"}
        tags/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))))

(deftest dissociate-tags-with-partial-match-query-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (testing "dissociate tag with only some of the collections matching the query are associated with the tag is OK"
    (let [coll1 (data-core/ingest "PROV1" (collection/collection {:entry-title "ET1"}))
          coll2 (data-core/ingest "PROV1" (collection/collection {:entry-title "ET2"}))
          token (echo-util/login (system/context) "user1")
          _ (index/wait-until-indexed)
          tag (tags/save-tag token (tags/make-tag {:tag-key "tag1"}) [coll1])
          assert-tag-associated (partial tags/assert-tag-associated-with-query token {:tag-key "tag1"})]
      (assert-tag-associated [coll1])
      (let [{:keys [status errors]} (tags/dissociate-by-query token "tag1" {:provider "PROV1"})]
        (is (= 200 status))
        (assert-tag-associated [])))))

(deftest dissociate-tags-with-mixed-response-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (testing "dissociate tag with mixed success and failure response"
    (let [coll1 (data-core/ingest "PROV1" (collection/collection {:entry-title "ET1"}))
          coll2 (data-core/ingest "PROV1" (collection/collection {:entry-title "ET2"}))
          coll3 (data-core/ingest "PROV1" (collection/collection {:entry-title "ET3"}))
          token (echo-util/login (system/context) "user1")
          tag-key "tag1"
          assert-tag-associated (partial tags/assert-tag-associated-with-query token {:tag-key "tag1"})]
      (tags/create-tag token (tags/make-tag {:tag-key tag-key}))
      (index/wait-until-indexed)
      (tags/associate-by-concept-ids token tag-key [{:concept-id (:concept-id coll1)}
                                                    {:concept-id (:concept-id coll2)
                                                     :revision-id (:revision-id coll2)}])
      (assert-tag-associated [coll1 coll2])

      (let [response (tags/dissociate-by-concept-ids
                      token tag-key
                      [{:concept-id "C100-P5"} ;; non-existent collection
                       {:concept-id (:concept-id coll1)} ;; success
                       {:concept-id (:concept-id coll2) :revision-id 1} ;; success
                       {:concept-id (:concept-id coll3)}])] ;; no tag association
        (tags/assert-tag-dissociation-response-error?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}
          ["C1200000012-PROV1"] {:concept-id "TA1200000016-CMR" :revision-id 2}
          ["C1200000013-PROV1" 1] {:concept-id "TA1200000017-CMR" :revision-id 2}
          ["C1200000014-PROV1"] {:warnings ["Tag [tag1] is not associated with collection [C1200000014-PROV1]."]}}
         response)
        (assert-tag-associated [])))))

;; This tests association retention when collections and tags are updated or deleted.
(deftest association-retention-test
  (echo-util/grant-all (system/context)
                       (echo-util/coll-catalog-item-id "PROV1"))
  (let [coll (data-core/ingest "PROV1" (collection/collection))
        token (echo-util/login (system/context) "user1")
        _ (index/wait-until-indexed)
        tag (tags/save-tag token (tags/make-tag {:tag-key "tag1"}) [coll])
        assert-tag-associated (partial tags/assert-tag-associated-with-query nil {:tag-key "tag1"})
        assert-tag-not-associated (fn []
                                    (let [refs (search/find-refs :collection {:tag-key "tag1"})]
                                      (is (nil? (:errors refs)))
                                      (is (data-core/refs-match? [] refs))))]
    (index/wait-until-indexed)

    (testing "Tag initially associated with collection"
      (assert-tag-associated [coll]))

    (testing "Tag still associated with collection after updating collection"
      (let [updated-coll (data-core/ingest "PROV1" (dissoc coll :revision-id))]
        (is (= 200 (:status updated-coll)))
        (index/wait-until-indexed)
        (assert-tag-associated [updated-coll])))

    (testing "Tag still associated with collection after deleting and recreating the collection"
      (is (= 200 (:status (ingest/delete-concept (data-core/item->concept coll)))))
      (let [recreated-coll (data-core/ingest "PROV1" (dissoc coll :revision-id))]
        (is (= 200 (:status recreated-coll)))
        (index/wait-until-indexed)
        (assert-tag-associated [recreated-coll])))

    (let [latest-coll (assoc coll :revision-id 4)]

      (testing "Tag still associated with collection after updating tag"
        (let [updated-tag (tags/save-tag token tag)]
          (is (= {:status 200 :revision-id 2} (select-keys updated-tag [:status :revision-id])))
          (index/wait-until-indexed)
          (assert-tag-associated [latest-coll])))

      (testing "Tag not associated with collection after deleting and recreating the tag"
        (is (= {:status 200 :concept-id (:concept-id tag) :revision-id 3}
               (tags/delete-tag token (:tag-key tag))))
        (index/wait-until-indexed)

        (testing "Not associated after tag deleted"
          (assert-tag-not-associated))

        (is (= {:status 200 :concept-id (:concept-id tag) :revision-id 4}
               (tags/create-tag token (tags/make-tag {:tag-key "tag1"}))))
        (index/wait-until-indexed)
        (testing "Not associated after being recreated."
          (assert-tag-not-associated))))))

(defn- assert-tag-association
  "Assert the collections are associated with the tag for the given tag-key"
  [token colls tag-key]
  (is (data-core/refs-match? colls
                             (search/find-refs :collection {:token token
                                                            :tag-key tag-key}))))

(deftest associate-dissociate-tag-with-collections-test
  ;; Grant all collections in PROV1
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (let [[coll1 coll2 coll3] (for [n (range 1 4)]
                              (data-core/ingest "PROV1" (collection/collection)))
        [coll1-id coll2-id coll3-id] (map :concept-id [coll1 coll2 coll3])
        token (echo-util/login (system/context) "user1")]
    (tags/create-tag token (tags/make-tag {:tag-key "tag1"}))
    (tags/create-tag token (tags/make-tag {:tag-key "tag2"}))
    (index/wait-until-indexed)

    ;; associate tag1 to coll1, tag2 to coll2
    ;; both :concept-id and :concept_id works as keys
    (tags/associate-by-concept-ids token "tag1" [{:concept_id coll1-id}])
    (tags/associate-by-concept-ids token "tag2" [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll1] "tag1")
    (assert-tag-association token [coll2] "tag2")

    ;; associate tag1 to coll1 again
    (tags/associate-by-concept-ids token "tag1" [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll1] "tag1")
    (assert-tag-association token [coll2] "tag2")

    ;; associate tag1 to coll2
    (tags/associate-by-concept-ids token "tag1" [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll1 coll2] "tag1")
    (assert-tag-association token [coll2] "tag2")

    ;; associate tag2 to coll1, coll2 and coll3
    (tags/associate-by-concept-ids token "tag2" [{:concept-id coll1-id}
                                                 {:concept-id coll2-id}
                                                 {:concept-id coll3-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll1 coll2] "tag1")
    (assert-tag-association token [coll1 coll2 coll3] "tag2")

    ;; dissociate tag1 from coll1
    (tags/dissociate-by-concept-ids token "tag1" [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll2] "tag1")
    (assert-tag-association token [coll1 coll2 coll3] "tag2")

    ;; dissociate tag2 from coll1 and coll2
    (tags/dissociate-by-concept-ids token "tag2" [{:concept-id coll1-id}
                                                  {:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll2] "tag1")
    (assert-tag-association token [coll3] "tag2")))

(deftest associate-tags-with-data-test
  (echo-util/grant-all (system/context)
                       (echo-util/coll-catalog-item-id "PROV1"))
  (let [coll (data-core/ingest "PROV1" (collection/collection))
        coll-concept-id (:concept-id coll)
        token (echo-util/login (system/context) "user1")
        tag-key "tag1"]
    (tags/create-tag token (tags/make-tag {:tag-key tag-key}))
    (index/wait-until-indexed)

    (testing "Associate tag with collections by concept-id and data"
      (are [data]
           (let [{:keys [status]} (tags/associate-by-concept-ids
                                   token tag-key [{:concept-id coll-concept-id
                                                   :data data}])]
             (is (= 200 status)))

        "string data"
        true
        100
        123.45
        [true "some string" 100]
        {"status" "reviewed" "action" "fix typos"}))

    (testing "Associate tag with collections with invalid data"
      (let [{:keys [status body]} (transmit-tag/associate-tag :concept-ids
                                                              (system/context)
                                                              tag-key
                                                              nil
                                                              {:raw? true
                                                               :http-options {:body "{{{{"}})
            error (-> body :errors first)]
        (is (= 400 status))
        (is (re-find #"Invalid JSON: A JSON Object can not directly nest another JSON Object"
                     error))))

    (testing "Associate tag with collections with data exceed 32KB"
      (let [too-much-data {"a" (tags/string-of-length 32768)}
            expected-msg (format
                          "Tag association data exceed the maximum length of 32KB for collection with concept id [%s] revision id [%s]."
                          coll-concept-id nil)
            response (tags/associate-by-concept-ids
                      token tag-key [{:concept-id coll-concept-id
                                      :data too-much-data}])]
        (tags/assert-tag-association-response-error?
         {[coll-concept-id] {:errors [expected-msg]}}
         response)))))

(deftest retrieve-concept-by-tag-association-concept-id-test
  (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                 (search/retrieve-concept
                                  "TA10000-CMR" nil {:throw-exceptions true}))]
    (testing "Retrieve concept by tag association concept-id is invalid"
      (is (= [400 ["Retrieving concept by concept id is not supported for concept type [tag-association]."]]
             [status errors])))))
