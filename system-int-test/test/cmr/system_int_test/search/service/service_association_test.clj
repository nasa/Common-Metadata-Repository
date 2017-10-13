(ns cmr.system-int-test.search.service.service-association-test
  "This tests associating services with collections."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as su]
   [cmr.system-int-test.utils.association-util :as au]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1"
                          "provguid2" "PROV2"
                          "provguid3" "PROV3"}
                         {:grant-all-search? false})
   su/grant-all-service-fixture]))

(deftest associate-services-by-concept-ids-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV2"))

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (doall (for [p ["PROV1" "PROV2" "PROV3"]
                                               n (range 1 5)]
                                           (:concept-id (d/ingest
                                                         p
                                                         (dc/collection
                                                          {:short-name (str "S" n)
                                                           :version-id (str "V" n)
                                                           :entry-title (str "ET" n)})))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        token (e/login (s/context) "user1")
        {:keys [concept-id]} (su/ingest-service-with-attrs {:Name "service1"})]
    (index/wait-until-indexed)

    (testing "Associate service with collections by concept-ids"
      (let [response (au/associate-by-concept-ids
                      token concept-id [{:concept-id c1-p1}
                                        {:concept-id c3-p2}])]
        (su/assert-service-association-response-ok?
         {[c1-p1] {:concept-id "SA1200000026-CMR"
                   :revision-id 1}
          [c3-p2] {:concept-id "SA1200000027-CMR"
                   :revision-id 1}}
         response)))

    (testing "Associate to no collections"
      (let [response (au/associate-by-concept-ids token concept-id [])]
        (au/assert-invalid-data-error
         ["At least one collection must be provided for service association."]
         response)))

    (testing "Associate to collection revision and whole collection at the same time"
      (let [response (au/associate-by-concept-ids
                      token concept-id [{:concept-id c1-p1}
                                        {:concept-id c1-p1 :revision-id 1}])]
        (au/assert-invalid-data-error
         [(format (str "Unable to create service association on a collection revision and the whole "
                       "collection at the same time for the following collections: %s.")
                  c1-p1)]
         response)))

    (testing "Associate to non-existent collections"
      (let [response (au/associate-by-concept-ids
                      token concept-id [{:concept-id "C100-P5"}])]
        (su/assert-service-association-response-ok?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Associate to deleted collections"
      (let [c1-p1-concept (mdb/get-concept c1-p1)
            _ (ingest/delete-concept c1-p1-concept)
            _ (index/wait-until-indexed)
            response (au/associate-by-concept-ids
                      token concept-id [{:concept-id c1-p1}])]
        (su/assert-service-association-response-ok?
         {[c1-p1] {:errors [(format "Collection [%s] does not exist or is not visible." c1-p1)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (au/associate-by-concept-ids token concept-id [{:concept-id c4-p3}])]
        (su/assert-service-association-response-ok?
         {[c4-p3] {:errors [(format "Collection [%s] does not exist or is not visible." c4-p3)]}}
         response)))

    (testing "Service association mixed response"
      (let [response (au/associate-by-concept-ids
                      token concept-id [{:concept-id c2-p1}
                                        {:concept-id "C100-P5"}])]
        (su/assert-service-association-response-ok?
         {[c2-p1] {:concept-id "VA1200000028-CMR"
                   :revision-id 1}
          ["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))))

(deftest associate-service-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [native-id "var123"
        token (e/login (s/context) "user1")
        var-concept (su/make-service-concept {:native-id native-id
                                               :Name "service1"
                                               :provider-id "PROV1"})
        {:keys [concept-id revision-id]} (su/ingest-service var-concept)
        coll-concept-id (:concept-id (d/ingest "PROV1" (dc/collection)))]
    (testing "Associate service using query sent with invalid content type"
      (are [associate-service-fn request-json]
        (= {:status 400,
            :errors
            ["The mime types specified in the content-type header [application/xml] are not supported."]}
           (associate-service-fn token concept-id request-json {:http-options {:content-type :xml}}))

        au/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate applies JSON Query validations"
      (are [associate-service-fn request-json message]
        (= {:status 400
            :errors [message]}
           (associate-service-fn token concept-id {:foo "bar"}))

        au/associate-by-concept-ids {:concept-id coll-concept-id}
        "instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"))

    (testing "Associate service that doesn't exist"
      (are [associate-service-fn request-json]
        (= {:status 404
            :errors ["Service could not be found with concept id [S12345-PROV1]"]}
           (associate-service-fn token "S12345-PROV1" request-json))

        au/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate deleted service"
      (ingest/delete-concept var-concept {:token token})
      (are [associate-service-fn request-json]
        (= {:status 404
            :errors [(format "Service with concept id [%s] was deleted." concept-id)]}
           (associate-service-fn token concept-id request-json))

        au/associate-by-concept-ids [{:concept-id coll-concept-id}]))))
