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
         {[c2-p1] {:concept-id "SA1200000028-CMR"
                   :revision-id 1}
          ["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))))

(deftest associate-service-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [native-id "var123"
        token (e/login (s/context) "user1")
        serv-concept (su/make-service-concept {:native-id native-id
                                               :Name "service1"
                                               :provider-id "PROV1"})
        {:keys [concept-id revision-id]} (su/ingest-service serv-concept)
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
      (ingest/delete-concept serv-concept {:token token})
      (are [associate-service-fn request-json]
        (= {:status 404
            :errors [(format "Service with concept id [%s] was deleted." concept-id)]}
           (associate-service-fn token concept-id request-json))

        au/associate-by-concept-ids [{:concept-id coll-concept-id}]))))

(deftest dissociate-services-with-collections-by-concept-ids-test
  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [group1-concept-id (e/get-or-create-group (s/context) "group1")
        ;; Grant all collections in PROV1 and 2
        _ (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
        _ (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV2"))
        _ (e/grant-group (s/context) group1-concept-id (e/coll-catalog-item-id "PROV3"))
        [c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (doall (for [p ["PROV1" "PROV2" "PROV3"]
                                               n (range 1 5)]
                                           (d/ingest p (dc/collection
                                                        {:short-name (str "S" n)
                                                         :version-id (str "V" n)
                                                         :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        service-name "service1"
        token (e/login (s/context) "user1")
        prov3-token (e/login (s/context) "prov3-user" [group1-concept-id])
        {:keys [concept-id]} (su/ingest-service-with-attrs {:Name service-name})
        assert-service-associated (partial su/assert-service-associated-with-query
                                           prov3-token {:service-concept-id concept-id})]
    (index/wait-until-indexed)
    ;; Associate the service with every collection
    (au/associate-by-concept-ids
     prov3-token
     concept-id
     (map #(hash-map :concept-id (:concept-id %)) all-colls))

    (testing "Successfully dissociate service with collections"
      (let [{:keys [status]} (au/dissociate-by-concept-ids
                              token
                              concept-id
                              (map #(hash-map :concept-id (:concept-id %)) all-prov1-colls))]
        (is (= 200 status))
        (assert-service-associated (concat all-prov2-colls all-prov3-colls))))

    (testing "Dissociate non-existent collections"
      (let [response (au/dissociate-by-concept-ids
                      token concept-id [{:concept-id "C100-P5"}])]
        (su/assert-service-dissociation-response-ok?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Dissociate to deleted collections"
      (let [c1-p2-concept-id (:concept-id c1-p2)
            c1-p2-concept (mdb/get-concept c1-p2-concept-id)
            _ (ingest/delete-concept c1-p2-concept)
            _ (index/wait-until-indexed)
            response (au/dissociate-by-concept-ids
                      token concept-id [{:concept-id c1-p2-concept-id}])]
        (su/assert-service-dissociation-response-ok?
         {["C1200000019-PROV2"] {:errors [(format "Collection [%s] does not exist or is not visible."
                                                  c1-p2-concept-id)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [coll-concept-id (:concept-id c4-p3)
            response (au/dissociate-by-concept-ids
                      token concept-id [{:concept-id coll-concept-id}])]
        (su/assert-service-dissociation-response-ok?
         {["C1200000026-PROV3"] {:errors [(format "Collection [%s] does not exist or is not visible."
                                                  coll-concept-id)]}}
         response)))))

(deftest dissociate-service-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [service-name "service1"
        token (e/login (s/context) "user1")
        serv-concept (su/make-service-concept {:Name service-name})
        {:keys [concept-id revision-id]} (su/ingest-service serv-concept)
        coll-concept-id (:concept-id (d/ingest "PROV1" (dc/collection)))]

    (testing "Dissociate service using query sent with invalid content type"
      (are [dissociate-service-fn request-json]
        (= {:status 400,
            :errors
            ["The mime types specified in the content-type header [application/xml] are not supported."]}
           (dissociate-service-fn
            token concept-id request-json {:http-options {:content-type :xml}}))

        au/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate applies JSON Query validations"
      (are [dissociate-service-fn request-json message]
        (= {:status 400
            :errors [message]}
           (dissociate-service-fn token concept-id request-json))

        au/dissociate-by-concept-ids {:concept-id coll-concept-id}
        "instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"))

    (testing "Dissociate service that doesn't exist"
      (are [dissociate-service-fn request-json]
        (= {:status 404
            :errors ["Service could not be found with concept id [S12345-PROV1]"]}
           (dissociate-service-fn token "S12345-PROV1" request-json))

        au/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate deleted service"
      (ingest/delete-concept serv-concept {:token token})
      (are [dissociate-service-fn request-json]
        (= {:status 404
            :errors [(format "Service with concept id [%s] was deleted." concept-id)]}
           (dissociate-service-fn token concept-id request-json))

        au/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))))

(deftest dissociate-services-with-mixed-response-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (testing "dissociate service with mixed success and failure response"
    (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "ET1"}))
          coll2 (d/ingest "PROV1" (dc/collection {:entry-title "ET2"}))
          coll3 (d/ingest "PROV1" (dc/collection {:entry-title "ET3"}))
          token (e/login (s/context) "user1")
          service-name "service1"
          {:keys [concept-id]} (su/ingest-service-with-attrs {:Name service-name})
          assert-service-associated (partial su/assert-service-associated-with-query
                                              token {:service-concept-id concept-id})]

      (index/wait-until-indexed)
      (au/associate-by-concept-ids token concept-id [{:concept-id (:concept-id coll1)}
                                                        {:concept-id (:concept-id coll2)
                                                         :revision-id (:revision-id coll2)}])
      (assert-service-associated [coll1 coll2])

      (let [response (au/dissociate-by-concept-ids
                      token concept-id
                      [{:concept-id "C100-P5"} ;; non-existent collection
                       {:concept-id (:concept-id coll1)} ;; success
                       {:concept-id (:concept-id coll2) :revision-id 1} ;; success
                       {:concept-id (:concept-id coll3)}])] ;; no service association

        (su/assert-service-dissociation-response-ok?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}
          ["C1200000012-PROV1"] {:concept-id "SA1200000016-CMR" :revision-id 2}
          ["C1200000013-PROV1" 1] {:concept-id "SA1200000017-CMR" :revision-id 2}
          ["C1200000014-PROV1"]
          {:warnings [(format "Service [%s] is not associated with collection [C1200000014-PROV1]."
                              concept-id)]}}
         response)
        (assert-service-associated [])))))

(deftest associate-dissociate-service-with-collections-test
  ;; Grant all collections in PROV1
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [[coll1 coll2 coll3] (doall (for [n (range 1 4)]
                                     (d/ingest "PROV1" (dc/collection))))
        [coll1-id coll2-id coll3-id] (map :concept-id [coll1 coll2 coll3])
        token (e/login (s/context) "user1")
        {service1-concept-id :concept-id} (su/ingest-service-with-attrs {:Name "service1"})
        {service2-concept-id :concept-id} (su/ingest-service-with-attrs {:Name "service2"})
        assert-service-association (fn [concept-id colls]
                                     (su/assert-service-associated-with-query
                                      token {:service-concept-id concept-id} colls))]
    (index/wait-until-indexed)

    ;; associate service1 to coll1, service2 to coll2
    ;; both :concept-id and :concept_id works as keys
    (au/associate-by-concept-ids token service1-concept-id [{:concept_id coll1-id}])
    (au/associate-by-concept-ids token service2-concept-id [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-service-association service1-concept-id [coll1])
    (assert-service-association service2-concept-id [coll2])

    ;; associate service1 to coll1 again
    (au/associate-by-concept-ids token service1-concept-id [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-service-association service1-concept-id [coll1])
    (assert-service-association service2-concept-id [coll2])

    ;; associate service1 to coll2
    (au/associate-by-concept-ids token service1-concept-id [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-service-association service1-concept-id [coll1 coll2])
    (assert-service-association service2-concept-id [coll2])

    ;; associate service2 to coll1, coll2 and coll3
    (au/associate-by-concept-ids token service2-concept-id [{:concept-id coll1-id}
                                                            {:concept-id coll2-id}
                                                            {:concept-id coll3-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-service-association service1-concept-id [coll1 coll2])
    (assert-service-association service2-concept-id [coll1 coll2 coll3])

    ;; dissociate service1 from coll1
    (au/dissociate-by-concept-ids token service1-concept-id [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-service-association service1-concept-id [coll2])
    (assert-service-association service2-concept-id [coll1 coll2 coll3])

    ;; dissociate service2 from coll1 and coll2
    (au/dissociate-by-concept-ids token service2-concept-id [{:concept-id coll1-id}
                                                             {:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-service-association service1-concept-id [coll2])
    (assert-service-association service2-concept-id [coll3])))
