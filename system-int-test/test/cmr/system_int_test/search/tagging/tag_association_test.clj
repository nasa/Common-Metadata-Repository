(ns cmr.system-int-test.search.tagging.tag-association-test
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

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                                          {:grant-all-search? false})
                       tags/grant-all-tag-fixture]))

(deftest associate-tags-by-query-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid2"))

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2] (for [p ["PROV1" "PROV2" "PROV3"]
                                        n (range 1 5)]
                                    (:concept-id (d/ingest p (dc/collection
                                                               {:short-name (str "S" n)
                                                                :version-id (str "V" n)
                                                                :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-colls (concat all-prov1-colls all-prov2-colls)
        tag (tags/make-tag)
        token (e/login (s/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (index/wait-until-indexed)

    (testing "Successfully Associate tag with collections"
      (let [response (tags/associate-by-query token concept-id {:provider "PROV1"})
            expected-saved-tag (assoc tag
                                      :originator-id "user1"
                                      :associated-concept-ids (set all-prov1-colls))]
        (is (= {:status 200 :concept-id concept-id :revision-id 2}
               response))
        (tags/assert-tag-saved expected-saved-tag "user1" concept-id 2)

        (testing "Associate using query that finds nothing"
          (let [response (tags/associate-by-query token concept-id {:provider "foo"})]
            (is (= {:status 200 :concept-id concept-id :revision-id 3}
                   response))
            (tags/assert-tag-saved expected-saved-tag "user1" concept-id 3)))

        (testing "ACLs are applied to collections found"
          ;; None of PROV3's collections are visible
          (let [response (tags/associate-by-query token concept-id {:provider "PROV3"})]
            (is (= {:status 200 :concept-id concept-id :revision-id 4}
                   response))
            (tags/assert-tag-saved expected-saved-tag "user1" concept-id 4)))


        (testing "Associate more collections"
          ;; Associates all the version 2 collections which is c2-p1 (already in) and c2-p2 (new)
          (let [response (tags/associate-by-query token concept-id {:version "v2"})
                expected-saved-tag (assoc expected-saved-tag
                                          :associated-concept-ids
                                          (set (cons c2-p2 all-prov1-colls)))]
            (is (= {:status 200 :concept-id concept-id :revision-id 5}
                   response))
            (tags/assert-tag-saved expected-saved-tag "user1" concept-id 5)))))))

(deftest associate-tag-failure-test
  (let [tag (tags/make-tag)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        valid-query {:provider "foo"}]

    (testing "Associate tag using query sent with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (tags/associate-by-query token concept-id valid-query {:http-options {:content-type :xml}}))))

    (testing "Associate without token"
      (is (= {:status 401
              :errors ["Tags cannot be modified without a valid user token."]}
             (tags/associate-by-query nil concept-id valid-query))))

    (testing "Associate applies JSON Query validations"
      (is (= {:status 400
              :errors ["/condition object instance has properties which are not allowed by the schema: [\"foo\"]"]}
             (tags/associate-by-query token concept-id {:foo "bar"}))))

    (testing "Associate tag that doesn't exist"
      (is (= {:status 404
              :errors ["Tag could not be found with concept id [T100-CMR]"]}
             (tags/associate-by-query token "T100-CMR" valid-query))))

    (testing "Associate deleted tag"
      (tags/delete-tag token concept-id)
      (is (= {:status 404
              :errors [(format "Tag with concept id [%s] was deleted." concept-id)]}
             (tags/associate-by-query token concept-id valid-query))))))

(deftest disassociate-tags-with-collections-test
  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid2"))
  (e/grant-group (s/context) "groupguid1" (e/coll-catalog-item-id "provguid3"))

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (for [p ["PROV1" "PROV2" "PROV3"]
                                        n (range 1 5)]
                                    (:concept-id (d/ingest p (dc/collection
                                                               {:short-name (str "S" n)
                                                                :version-id (str "V" n)
                                                                :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        tag (tags/make-tag)
        token (e/login (s/context) "user1")
        prov3-token (e/login (s/context) "prov3-user" ["groupguid1"])
        {:keys [concept-id]} (tags/create-tag token tag)
        expected-saved-tag (assoc tag
                                  :originator-id "user1"
                                  :associated-concept-ids (set all-colls))]
    (index/wait-until-indexed)
    ;; Associate the tag with every collection
    (tags/associate-by-query prov3-token concept-id {:or [{:provider "PROV1"}
                                                          {:provider "PROV2"}
                                                          {:provider "PROV3"}]})
    (tags/assert-tag-saved expected-saved-tag "prov3-user" concept-id 2)

    (testing "Disassociate using query that finds nothing"
      (let [response (tags/disassociate-by-query token concept-id {:provider "foo"})]
        (is (= {:status 200 :concept-id concept-id :revision-id 3}
               response))
        (tags/assert-tag-saved expected-saved-tag "user1" concept-id 3)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible to normal users
      (let [response (tags/disassociate-by-query token concept-id {:provider "PROV3"})]
        (is (= {:status 200 :concept-id concept-id :revision-id 4}
               response))
        (tags/assert-tag-saved expected-saved-tag "user1" concept-id 4)))

    (testing "Successfully disassociate tag with collections"
      (let [response (tags/disassociate-by-query token concept-id {:provider "PROV1"})
            expected-saved-tag (assoc expected-saved-tag
                                      :associated-concept-ids (set (concat all-prov2-colls
                                                                           all-prov3-colls)))]
        (is (= {:status 200 :concept-id concept-id :revision-id 5}
               response))
        (tags/assert-tag-saved expected-saved-tag "user1" concept-id 5)))))

(deftest disassociate-tag-failure-test
  (let [tag (tags/make-tag)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        valid-query {:provider "foo"}]

    (testing "Disassociate tag using query sent with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (tags/disassociate-by-query token concept-id valid-query {:http-options {:content-type :xml}}))))

    (testing "Disassociate without token"
      (is (= {:status 401
              :errors ["Tags cannot be modified without a valid user token."]}
             (tags/disassociate-by-query nil concept-id valid-query))))

    (testing "Disassociate applies JSON Query validations"
      (is (= {:status 400
              :errors ["/condition object instance has properties which are not allowed by the schema: [\"foo\"]"]}
             (tags/disassociate-by-query token concept-id {:foo "bar"}))))

    (testing "Disassociate tag that doesn't exist"
      (is (= {:status 404
              :errors ["Tag could not be found with concept id [T100-CMR]"]}
             (tags/disassociate-by-query token "T100-CMR" valid-query))))

    (testing "Disassociate deleted tag"
      (tags/delete-tag token concept-id)
      (is (= {:status 404
              :errors [(format "Tag with concept id [%s] was deleted." concept-id)]}
             (tags/disassociate-by-query token concept-id valid-query))))))

;; This tests association retention when collections and tags are updated or deleted.
(deftest association-retention-test
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid1"))
  (let [coll (d/ingest "PROV1" (dc/collection))
        token (e/login (s/context) "user1")
        _ (index/wait-until-indexed)
        tag (tags/save-tag token (tags/make-tag {:value "tag1"}) [coll])
        assert-tag-associated (fn [collection]
                                (is (d/refs-match? [collection]
                                                   (search/find-refs :collection {:tag-value "tag1"}))))
        assert-tag-not-associated (fn []
                                    (is (d/refs-match?
                                          []
                                          (search/find-refs :collection {:tag-value "tag1"}))))]
    (index/wait-until-indexed)

    (testing "Tag initially associated with collection"
      (assert-tag-associated coll))

    (testing "Tag still associated with collection after updating collection"
      (let [updated-coll (d/ingest "PROV1" (dissoc coll :revision-id))]
        (is (= 200 (:status updated-coll)))
        (index/wait-until-indexed)
        (assert-tag-associated updated-coll)))

    (testing "Tag still associated with collection after deleting and recreating the collection"
      (is (= 200 (:status (ingest/delete-concept (d/item->concept coll)))))
      (let [recreated-coll (d/ingest "PROV1" (dissoc coll :revision-id))]
        (is (= 200 (:status recreated-coll)))
        (index/wait-until-indexed)
        (assert-tag-associated recreated-coll)))

    (let [latest-coll (assoc coll :revision-id 4)]

      (testing "Tag still associated with collection after updating tag"
        (let [updated-tag (tags/save-tag token tag)]
          (is (= {:status 200 :revision-id 3} (select-keys updated-tag [:status :revision-id])))
          (index/wait-until-indexed)
          (assert-tag-associated latest-coll)))

      (testing "Tag not associated with collection after deleting and recreating the tag"
        (is (= {:status 200 :concept-id (:concept-id tag) :revision-id 4}
               (tags/delete-tag token (:concept-id tag))))
        (index/wait-until-indexed)

        (testing "Not associated after tag deleted"
          (assert-tag-not-associated)))

      (is (= {:status 200 :concept-id (:concept-id tag) :revision-id 5}
             (tags/create-tag token (tags/make-tag {:value "tag1"}))))
      (index/wait-until-indexed)
      (testing "Not associated after being recreated."
        (assert-tag-not-associated)))))


