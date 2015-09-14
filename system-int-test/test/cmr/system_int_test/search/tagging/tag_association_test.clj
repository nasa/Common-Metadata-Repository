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

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                                          {:grant-all-search? false}))

(deftest associate-tags-with-collections-test

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
        tag (tags/make-tag 1)
        token (e/login (s/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (index/wait-until-indexed)

    (testing "Successfully Associate tag with collections"
      (let [response (tags/associate token concept-id {:provider "PROV1"})
            expected-saved-tag (assoc tag
                                      :originator-id "user1"
                                      :associated-collection-ids (set all-prov1-colls))]
        (is (= {:status 200 :concept-id concept-id :revision-id 2}
               response))
        (tags/assert-tag-saved expected-saved-tag "user1" concept-id 2)

        (testing "Associate using query that finds nothing"
          (let [response (tags/associate token concept-id {:provider "foo"})]
            (is (= {:status 200 :concept-id concept-id :revision-id 3}
                   response))
            (tags/assert-tag-saved expected-saved-tag "user1" concept-id 3)))

        (testing "ACLs are applied to collections found"
          ;; None of PROV3's collections are visible
          (let [response (tags/associate token concept-id {:provider "PROV3"})]
            (is (= {:status 200 :concept-id concept-id :revision-id 4}
                   response))
            (tags/assert-tag-saved expected-saved-tag "user1" concept-id 4)))


        (testing "Associate more collections"
          ;; Associates all the version 2 collections which is c2-p1 (already in) and c2-p2 (new)
          (let [response (tags/associate token concept-id {:version "v2"})
                expected-saved-tag (assoc expected-saved-tag
                                          :associated-collection-ids
                                          (set (cons c2-p2 all-prov1-colls)))]
            (is (= {:status 200 :concept-id concept-id :revision-id 5}
                   response))
            (tags/assert-tag-saved expected-saved-tag "user1" concept-id 5)))))))

(deftest associate-tag-failure-test
  (let [tag (tags/make-tag 1)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")
        valid-query {:provider "foo"}]

    (testing "Associate tag using query sent with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (tags/associate token concept-id valid-query {:http-options {:content-type :xml}}))))

    (testing "Associate without token"
      (is (= {:status 401
              :errors ["Tags cannot be modified without a valid user token."]}
             (tags/associate nil concept-id valid-query))))

    (testing "Associate applies JSON Query validations"
      (is (= {:status 400
              :errors ["/condition object instance has properties which are not allowed by the schema: [\"foo\"]"]}
             (tags/associate token concept-id {:foo "bar"}))))

    (testing "Associate tag that doesn't exist"
      (is (= {:status 404
              :errors ["Tag could not be found with concept id [T100-CMR]"]}
             (tags/associate token "T100-CMR" valid-query))))

    (testing "Associate deleted tag"
      (tags/delete-tag token concept-id)
      (is (= {:status 404
              :errors [(format "Tag with concept id [%s] was deleted." concept-id)]}
             (tags/associate token concept-id valid-query))))))


;; Later TODOs

;; Deleting a tag dissociates the collections
;; - test this by searching for collection by that tag

;; Updating a tag maintains associations
;; - test this by search for collections by that tag

;; Collection deleted and then reingested is still associated with a tag
;; - test this by search for collections by that tag
