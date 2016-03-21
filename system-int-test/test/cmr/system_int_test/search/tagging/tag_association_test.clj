(ns cmr.system-int-test.search.tagging.tag-association-test
  "This tests associating tags with collections."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                                             {:grant-all-search? false})
                       tags/grant-all-tag-fixture]))

(defn- assert-tag-association-response-ok?
  "Returns true if the tag association response is correct."
  [expected-status expected-tag-associations response]
  (let [{:keys [status body errors]} response
        body (if (= 200 status)
               (set body)
               (set errors))]
    (is (= [expected-status (set expected-tag-associations)]
           [status body]))))

(deftest associate-tags-by-query-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid2"))

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
        all-colls (concat all-prov1-colls all-prov2-colls)
        tag (tags/make-tag)
        tag-key (:tag-key tag)
        token (e/login (s/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (index/wait-until-indexed)

    (testing "Successfully Associate tag with collections"
      (let [response (tags/associate-by-query token tag-key {:provider "PROV1"})]
        (assert-tag-association-response-ok? 200
                                             [{:concept-id "TA1200000013-CMR", :revision-id 1}
                                              {:concept-id "TA1200000014-CMR", :revision-id 1}
                                              {:concept-id "TA1200000015-CMR", :revision-id 1}
                                              {:concept-id "TA1200000016-CMR", :revision-id 1}]
                                             response)))

    (testing "Associate using query that finds nothing"
      (let [response (tags/associate-by-query token tag-key {:provider "foo"})]
        (assert-tag-association-response-ok? 200 [] response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (tags/associate-by-query token tag-key {:provider "PROV3"})]
        (assert-tag-association-response-ok? 200 [] response)))

    (testing "Associate more collections"
      ;; Associates all the version 2 collections which is c2-p1 (already in) and c2-p2 (new)
      (let [response (tags/associate-by-query token tag-key {:version "v2"})]
        (assert-tag-association-response-ok? 200
                                             [{:concept-id "TA1200000014-CMR", :revision-id 2}
                                              {:concept-id "TA1200000017-CMR", :revision-id 1}]
                                             response)))))

(deftest associate-tags-by-concept-ids-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid2"))

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
        all-colls (concat all-prov1-colls all-prov2-colls)
        tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (e/login (s/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (index/wait-until-indexed)

    (testing "Associate tag with collections by concept-ids"
      (let [response (tags/associate-by-concept-ids
                       token tag-key [{:concept-id c1-p1}
                                      {:concept-id c3-p2}])]
        (assert-tag-association-response-ok? 200
                                             [{:concept-id "TA1200000013-CMR", :revision-id 1}
                                              {:concept-id "TA1200000014-CMR", :revision-id 1}]
                                             response)))

    (testing "Associate to deleted or non-existent collections"
      (let [c1-p1-concept (mdb/get-concept c1-p1)
            _ (ingest/delete-concept c1-p1-concept)
            _ (index/wait-until-indexed)
            response (tags/associate-by-concept-ids
                       token tag-key [{:concept-id c1-p1}
                                      {:concept-id c2-p1}
                                      {:concept-id "C100-P5"}])]
        (assert-tag-association-response-ok?
          422
          [(format "The following collections do not exist or are not accessible: C100-P5, %s." c1-p1)]
          response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (tags/associate-by-concept-ids token tag-key [{:concept-id c4-p3}])]
        (assert-tag-association-response-ok?
          422
          [(format "The following collections do not exist or are not accessible: %s." c4-p3)]
          response)))))

(deftest associate-tag-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (let [tag (tags/make-tag)
        tag-key (:tag-key tag)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        coll-concept-id (:concept-id (d/ingest "PROV1" (dc/collection)))]
    (testing "Associate tag using query sent with invalid content type"
      (are [associate-tag-fn request-json]
           (= {:status 400,
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
           "/condition object instance has properties which are not allowed by the schema: [\"foo\"]"

           tags/associate-by-concept-ids {:concept-id coll-concept-id}
           "instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"))

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

(deftest disassociate-tags-with-collections-by-query-test
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
                                    (d/ingest p (dc/collection
                                                  {:short-name (str "S" n)
                                                   :version-id (str "V" n)
                                                   :entry-title (str "ET" n)})))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (e/login (s/context) "user1")
        prov3-token (e/login (s/context) "prov3-user" ["groupguid1"])
        {:keys [concept-id]} (tags/create-tag token tag)
        assert-tag-associated (fn [expected-colls query]
                                (is (d/refs-match?
                                      expected-colls
                                      (search/find-refs :collection
                                                        (assoc query
                                                               :token prov3-token
                                                               :page_size 30)))))]
    (index/wait-until-indexed)
    ;; Associate the tag with every collection
    (tags/associate-by-query prov3-token tag-key {:or [{:provider "PROV1"}
                                                       {:provider "PROV2"}
                                                       {:provider "PROV3"}]})

    (testing "Disassociate using query that finds nothing"
      (let [{:keys [status]} (tags/disassociate-by-query token tag-key {:provider "foo"})]
        (is (= 200 status))
        (assert-tag-associated all-colls {:tag-key "tag1"})))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible to normal users
      (let [{:keys [status]} (tags/disassociate-by-query token tag-key {:provider "PROV3"})]
        (is (= 200 status))
        (assert-tag-associated all-colls {:tag-key "tag1"})))

    (testing "Successfully disassociate tag with collections"
      (let [{:keys [status]} (tags/disassociate-by-query token tag-key {:provider "PROV1"})]
        (is (= 200 status))
        (assert-tag-associated
          (concat all-prov2-colls all-prov3-colls) {:tag-key "tag1"})))))

(deftest disassociate-tags-with-collections-by-concept-ids-test
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
                                    (d/ingest p (dc/collection
                                                  {:short-name (str "S" n)
                                                   :version-id (str "V" n)
                                                   :entry-title (str "ET" n)})))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        tag-key "tag1"
        tag (tags/make-tag {:tag-key tag-key})
        token (e/login (s/context) "user1")
        prov3-token (e/login (s/context) "prov3-user" ["groupguid1"])
        {:keys [concept-id]} (tags/create-tag token tag)
        assert-tag-associated (fn [expected-colls query]
                                (is (d/refs-match?
                                      expected-colls
                                      (search/find-refs :collection
                                                        (assoc query
                                                               :token prov3-token
                                                               :page_size 30)))))]
    (index/wait-until-indexed)
    ;; Associate the tag with every collection
    (tags/associate-by-query prov3-token tag-key {:or [{:provider "PROV1"}
                                                       {:provider "PROV2"}
                                                       {:provider "PROV3"}]})

    (testing "Successfully disassociate tag with collections"
      (let [{:keys [status]} (tags/disassociate-by-concept-ids
                               token
                               tag-key
                               (map #(hash-map :concept-id (:concept-id %)) all-prov1-colls))]
        (is (= 200 status))
        (assert-tag-associated
          (concat all-prov2-colls all-prov3-colls) {:tag-key "tag1"})))

    (testing "Disassociate to deleted or non-existent collections"
      (let [c1-p2-concept-id (:concept-id c1-p2)
            c1-p2-concept (mdb/get-concept c1-p2-concept-id)
            _ (ingest/delete-concept c1-p2-concept)
            _ (index/wait-until-indexed)
            response (tags/disassociate-by-concept-ids
                       token tag-key [{:concept-id c1-p2-concept-id}
                                      {:concept-id (:concept-id c2-p2)}
                                      {:concept-id "C100-P5"}])]
        (assert-tag-association-response-ok?
          422
          [(format "The following collections do not exist or are not accessible: C100-P5, %s."
                   c1-p2-concept-id)]
          response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [coll-concept-id (:concept-id c4-p3)
            response (tags/disassociate-by-concept-ids
                       token tag-key [{:concept-id coll-concept-id}])]
        (assert-tag-association-response-ok?
          422
          [(format "The following collections do not exist or are not accessible: %s." coll-concept-id)]
          response)))))

(deftest disassociate-tag-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (let [tag (tags/make-tag)
        tag-key (:tag-key tag)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        coll-concept-id (:concept-id (d/ingest "PROV1" (dc/collection)))]

    (testing "Disassociate tag using query sent with invalid content type"
      (are [disassociate-tag-fn request-json]
           (= {:status 400,
               :errors
               ["The mime types specified in the content-type header [application/xml] are not supported."]}
              (disassociate-tag-fn token tag-key request-json {:http-options {:content-type :xml}}))
           tags/disassociate-by-query {:provider "foo"}
           tags/disassociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Disassociate applies JSON Query validations"
      (are [disassociate-tag-fn request-json message]
           (= {:status 400
               :errors [message]}
              (disassociate-tag-fn token tag-key request-json))
           tags/disassociate-by-query {:foo "bar"}
           "/condition object instance has properties which are not allowed by the schema: [\"foo\"]"

           tags/disassociate-by-concept-ids {:concept-id coll-concept-id}
           "instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"))

    (testing "Disassociate tag that doesn't exist"
      (are [disassociate-tag-fn request-json]
           (= {:status 404
               :errors ["Tag could not be found with tag-key [tag100]"]}
              (disassociate-tag-fn token "tag100" request-json))
           tags/disassociate-by-query {:provider "foo"}
           tags/disassociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Disassociate deleted tag"
      (tags/delete-tag token tag-key)
      (are [disassociate-tag-fn request-json]
           (= {:status 404
               :errors [(format "Tag with tag-key [%s] was deleted." tag-key)]}
              (disassociate-tag-fn token tag-key request-json))
           tags/disassociate-by-query {:provider "foo"}
           tags/disassociate-by-concept-ids [{:concept-id coll-concept-id}]))))

;; This tests association retention when collections and tags are updated or deleted.
(deftest association-retention-test
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid1"))
  (let [coll (d/ingest "PROV1" (dc/collection))
        token (e/login (s/context) "user1")
        _ (index/wait-until-indexed)
        tag (tags/save-tag token (tags/make-tag {:tag-key "tag1"}) [coll])
        assert-tag-associated (fn [collection]
                                (let [refs (search/find-refs :collection {:tag-key "tag1"})]
                                  (is (nil? (:errors refs)))
                                  (is (d/refs-match?
                                        [collection]
                                        (search/find-refs :collection {:tag-key "tag1"})))))
        assert-tag-not-associated (fn []
                                    (let [refs (search/find-refs :collection {:tag-key "tag1"})]
                                      (is (nil? (:errors refs)))
                                      (is (d/refs-match? [] refs))))]
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
          (is (= {:status 200 :revision-id 2} (select-keys updated-tag [:status :revision-id])))
          (index/wait-until-indexed)
          (assert-tag-associated latest-coll)))

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
  (is (d/refs-match? colls
                     (search/find-refs :collection {:token token
                                                    :tag-key tag-key}))))

(deftest associate-disassociate-tag-with-collections-test
  ;; Grant all collections in PROV1
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "provguid1"))
  (let [[coll1 coll2 coll3] (for [n (range 1 4)]
                              (d/ingest "PROV1" (dc/collection)))
        [coll1-id coll2-id coll3-id] (map :concept-id [coll1 coll2 coll3])
        token (e/login (s/context) "user1")]
    (tags/create-tag token (tags/make-tag {:tag-key "tag1"}))
    (tags/create-tag token (tags/make-tag {:tag-key "tag2"}))
    (index/wait-until-indexed)

    ;; associate tag1 to coll1, tag2 to coll2
    (tags/associate-by-concept-ids token "tag1" [{:concept-id coll1-id}])
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

    ;; disassociate tag1 from coll1
    (tags/disassociate-by-concept-ids token "tag1" [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll2] "tag1")
    (assert-tag-association token [coll1 coll2 coll3] "tag2")

    ;; disassociate tag2 from coll1 and coll2
    (tags/disassociate-by-concept-ids token "tag2" [{:concept-id coll1-id}
                                                    {:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-tag-association token [coll2] "tag1")
    (assert-tag-association token [coll3] "tag2")))

(deftest associate-tags-with-data-test
  (e/grant-all (s/context) (e/coll-catalog-item-id "provguid1"))
  (let [coll (d/ingest "PROV1" (dc/collection))
        token (e/login (s/context) "user1")
        tag-key "tag1"
        _ (tags/create-tag token (tags/make-tag {:tag-key tag-key}))]
    (index/wait-until-indexed)

    (testing "Associate tag with collections by concept-id and data"
      (are [data]
           (let [{:keys [status]} (tags/associate-by-concept-ids
                                    token tag-key [{:concept-id (:concept-id coll)
                                                    :data data}])]
             (is (= 200 status)))

           "string data"
           true
           100
           123.45
           [true "some string" 100]
           {"status" "reviewed" "action" "fix typos"}))))

