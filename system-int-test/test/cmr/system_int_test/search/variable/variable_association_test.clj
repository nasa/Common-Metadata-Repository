(ns cmr.system-int-test.search.variable.variable-association-test
  "This tests associating variables with collections."
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
   [cmr.system-int-test.utils.variable-util :as vu]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                         {:grant-all-search? false})
   vu/grant-all-variable-fixture]))

(deftest associate-variables-by-concept-ids-with-collections-test

  ;; Grant all collections in PROV1 and 2
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV2"))

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
        variable-name "variable1"
        variable (vu/make-variable {:Name variable-name})
        token (e/login (s/context) "user1")
        {:keys [concept-id] :as response} (vu/create-variable token variable)]
    (index/wait-until-indexed)

    (testing "Associate variable with collections by concept-ids"
      (let [response (vu/associate-by-concept-ids
                      token variable-name [{:concept-id c1-p1}
                                           {:concept-id c3-p2}])]
        (vu/assert-variable-association-response-ok?
         {["C1200000013-PROV1"] {:concept-id "VA1200000026-CMR"
                                 :revision-id 1}
          ["C1200000019-PROV2"] {:concept-id "VA1200000027-CMR"
                                 :revision-id 1}}
         response)))

    (testing "Associate to no collections"
      (let [response (vu/associate-by-concept-ids token variable-name [])]
        (vu/assert-invalid-data-error
         ["At least one collection must be provided for variable association."]
         response)))

    (testing "Associate to collection revision and whole collection at the same time"
      (let [response (vu/associate-by-concept-ids
                      token variable-name [{:concept-id c1-p1}
                                           {:concept-id c1-p1 :revision-id 1}])]
        (vu/assert-invalid-data-error
         [(format (str "Unable to create variable association on a collection revision and the whole "
                       "collection at the same time for the following collections: %s.")
                  c1-p1)]
         response)))

    (testing "Associate to non-existent collections"
      (let [response (vu/associate-by-concept-ids
                      token variable-name [{:concept-id "C100-P5"}])]
        (vu/assert-variable-association-response-ok?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Associate to deleted collections"
      (let [c1-p1-concept (mdb/get-concept c1-p1)
            _ (ingest/delete-concept c1-p1-concept)
            _ (index/wait-until-indexed)
            response (vu/associate-by-concept-ids
                      token variable-name [{:concept-id c1-p1}])]
        (vu/assert-variable-association-response-ok?
         {[c1-p1] {:errors [(format "Collection [%s] does not exist or is not visible." c1-p1)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (vu/associate-by-concept-ids token variable-name [{:concept-id c4-p3}])]
        (vu/assert-variable-association-response-ok?
         {[c4-p3] {:errors [(format "Collection [%s] does not exist or is not visible." c4-p3)]}}
         response)))

    (testing "Variable association mixed response"
      (let [response (vu/associate-by-concept-ids
                      token variable-name [{:concept-id c2-p1}
                                           {:concept-id "C100-P5"}])]
        (vu/assert-variable-association-response-ok?
         {["C1200000014-PROV1"] {:concept-id "VA1200000028-CMR"
                                 :revision-id 1}
          ["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))))

(deftest associate-variable-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [variable-name "variable1"
        variable (vu/make-variable {:Name variable-name})
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (vu/create-variable token variable)
        ;; The stored updated variable would have user1 in the originator id
        variable (assoc variable :originator-id "user1")
        coll-concept-id (:concept-id (d/ingest "PROV1" (dc/collection)))]
    (testing "Associate variable using query sent with invalid content type"
      (are [associate-variable-fn request-json]
        (= {:status 400,
            :errors
            ["The mime types specified in the content-type header [application/xml] are not supported."]}
           (associate-variable-fn token variable-name request-json {:http-options {:content-type :xml}}))
        ; vu/associate-by-query {:provider "foo"}
        vu/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate applies JSON Query validations"
      (are [associate-variable-fn request-json message]
        (= {:status 400
            :errors [message]}
           (associate-variable-fn token variable-name {:foo "bar"}))

        ; vu/associate-by-query {:foo "bar"}
        ; "/condition object instance has properties which are not allowed by the schema: [\"foo\"]"

        vu/associate-by-concept-ids {:concept-id coll-concept-id}
        "instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"))

    (testing "Associate variable that doesn't exist"
      (are [associate-variable-fn request-json]
        (= {:status 404
            :errors ["Variable could not be found with variable-name [variable100]"]}
           (associate-variable-fn token "variable100" request-json))
        ; vu/associate-by-query {:provider "foo"}
        vu/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    ;; This test is commented out since we don't support delete variable yet
    ;; Once we support delete variable, re-enable this test.
    ;; See CMR-4168
    #_(testing "Associate deleted variable"
        (vu/delete-variable token variable-name)
        (are [associate-variable-fn request-json]
          (= {:status 404
              :errors [(format "Variable with variable-name [%s] was deleted." variable-name)]}
             (associate-variable-fn token variable-name request-json))
          ; vu/associate-by-query {:provider "foo"}
          vu/associate-by-concept-ids [{:concept-id coll-concept-id}]))))


(deftest dissociate-variables-with-collections-by-concept-ids-test
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
        variable-name "variable1"
        variable (vu/make-variable {:Name variable-name})
        token (e/login (s/context) "user1")
        prov3-token (e/login (s/context) "prov3-user" [group1-concept-id])
        {:keys [concept-id]} (vu/create-variable token variable)
        assert-variable-associated (partial vu/assert-variable-associated-with-query
                                            prov3-token {:variable-name "variable1"})]
    (index/wait-until-indexed)
    ;; Associate the variable with every collection
    (vu/associate-by-concept-ids
     prov3-token
     variable-name
     (map #(hash-map :concept-id (:concept-id %)) all-colls))

    (testing "Successfully dissociate variable with collections"
      (let [{:keys [status]} (vu/dissociate-by-concept-ids
                              token
                              variable-name
                              (map #(hash-map :concept-id (:concept-id %)) all-prov1-colls))]
        (is (= 200 status))
        (assert-variable-associated (concat all-prov2-colls all-prov3-colls))))

    (testing "Dissociate non-existent collections"
      (let [response (vu/dissociate-by-concept-ids
                      token variable-name [{:concept-id "C100-P5"}])]
        (vu/assert-variable-dissociation-response-ok?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Dissociate to deleted collections"
      (let [c1-p2-concept-id (:concept-id c1-p2)
            c1-p2-concept (mdb/get-concept c1-p2-concept-id)
            _ (ingest/delete-concept c1-p2-concept)
            _ (index/wait-until-indexed)
            response (vu/dissociate-by-concept-ids
                      token variable-name [{:concept-id c1-p2-concept-id}])]
        (vu/assert-variable-dissociation-response-ok?
         {["C1200000019-PROV2"] {:errors [(format "Collection [%s] does not exist or is not visible."
                                                  c1-p2-concept-id)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [coll-concept-id (:concept-id c4-p3)
            response (vu/dissociate-by-concept-ids
                      token variable-name [{:concept-id coll-concept-id}])]
        (vu/assert-variable-dissociation-response-ok?
         {["C1200000026-PROV3"] {:errors [(format "Collection [%s] does not exist or is not visible."
                                                  coll-concept-id)]}}
         response)))))

(deftest dissociate-variable-failure-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [variable-name "variable1"
        variable (vu/make-variable {:Name variable-name})
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (vu/create-variable token variable)
        ;; The stored updated variable would have user1 in the originator id
        variable (assoc variable :originator-id "user1")
        coll-concept-id (:concept-id (d/ingest "PROV1" (dc/collection)))]

    (testing "Dissociate variable using query sent with invalid content type"
      (are [dissociate-variable-fn request-json]
        (= {:status 400,
            :errors
            ["The mime types specified in the content-type header [application/xml] are not supported."]}
           (dissociate-variable-fn token variable-name request-json {:http-options {:content-type :xml}}))
        ; vu/dissociate-by-query {:provider "foo"}
        vu/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate applies JSON Query validations"
      (are [dissociate-variable-fn request-json message]
        (= {:status 400
            :errors [message]}
           (dissociate-variable-fn token variable-name request-json))
        ; vu/dissociate-by-query {:foo "bar"}
        ; "/condition object instance has properties which are not allowed by the schema: [\"foo\"]"

        vu/dissociate-by-concept-ids {:concept-id coll-concept-id}
        "instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"))

    (testing "Dissociate variable that doesn't exist"
      (are [dissociate-variable-fn request-json]
        (= {:status 404
            :errors ["Variable could not be found with variable-name [variable100]"]}
           (dissociate-variable-fn token "variable100" request-json))
        ; vu/dissociate-by-query {:provider "foo"}
        vu/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    ;; This test is commented out since we don't support delete variable yet
    ;; Once we support delete variable, re-enable this test.
    ;; See CMR-4168.
    #_(testing "Dissociate deleted variable"
        (vu/delete-variable token variable-name)
        (are [dissociate-variable-fn request-json]
          (= {:status 404
              :errors [(format "Variable with variable-name [%s] was deleted." variable-name)]}
             (dissociate-variable-fn token variable-name request-json))
          ; vu/dissociate-by-query {:provider "foo"}
          vu/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))))

(deftest dissociate-variables-with-mixed-response-test
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (testing "dissociate variable with mixed success and failure response"
    (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "ET1"}))
          coll2 (d/ingest "PROV1" (dc/collection {:entry-title "ET2"}))
          coll3 (d/ingest "PROV1" (dc/collection {:entry-title "ET3"}))
          token (e/login (s/context) "user1")
          variable-name "variable1"
          assert-variable-associated (partial vu/assert-variable-associated-with-query
                                              token {:variable-name "variable1"})]
      (vu/create-variable token (vu/make-variable {:Name variable-name}))
      (index/wait-until-indexed)
      (vu/associate-by-concept-ids token variable-name [{:concept-id (:concept-id coll1)}
                                                        {:concept-id (:concept-id coll2)
                                                         :revision-id (:revision-id coll2)}])
      (assert-variable-associated [coll1 coll2])

      (let [response (vu/dissociate-by-concept-ids
                      token variable-name
                      [{:concept-id "C100-P5"} ;; non-existent collection
                       {:concept-id (:concept-id coll1)} ;; success
                       {:concept-id (:concept-id coll2) :revision-id 1} ;; success
                       {:concept-id (:concept-id coll3)}])] ;; no variable association

        (vu/assert-variable-dissociation-response-ok?
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}
          ["C1200000012-PROV1"] {:concept-id "VA1200000016-CMR" :revision-id 2}
          ["C1200000013-PROV1" 1] {:concept-id "VA1200000017-CMR" :revision-id 2}
          ["C1200000014-PROV1"] {:warnings ["Variable [variable1] is not associated with collection [C1200000014-PROV1]."]}}
         response)
        (assert-variable-associated [])))))

;; See CMR-4167, CMR-4168
; ;; This tests association retention when collections and variables are updated or deleted.
; (deftest association-retention-test
;   (e/grant-all (s/context) (e/coll-catalog-item-id "PROV1"))
;   (let [coll (d/ingest "PROV1" (dc/collection))
;         token (e/login (s/context) "user1")
;         _ (index/wait-until-indexed)
;         variable (vu/save-variable token (vu/make-variable {:Name "variable1"}) [coll])
;         assert-variable-associated (partial vu/assert-variable-associated-with-query nil {:Name "variable1"})
;         assert-variable-not-associated (fn []
;                                     (let [refs (search/find-refs :collection {:Name "variable1"})]
;                                       (is (nil? (:errors refs)))
;                                       (is (d/refs-match? [] refs))))]
;     (index/wait-until-indexed)
;
;     (testing "Variable initially associated with collection"
;       (assert-variable-associated [coll]))
;
;     (testing "Variable still associated with collection after updating collection"
;       (let [updated-coll (d/ingest "PROV1" (dissoc coll :revision-id))]
;         (is (= 200 (:status updated-coll)))
;         (index/wait-until-indexed)
;         (assert-variable-associated [updated-coll])))
;
;     (testing "Variable still associated with collection after deleting and recreating the collection"
;       (is (= 200 (:status (ingest/delete-concept (d/item->concept coll)))))
;       (let [recreated-coll (d/ingest "PROV1" (dissoc coll :revision-id))]
;         (is (= 200 (:status recreated-coll)))
;         (index/wait-until-indexed)
;         (assert-variable-associated [recreated-coll])))
;
;     (let [latest-coll (assoc coll :revision-id 4)]
;
;       (testing "Variable still associated with collection after updating variable"
;         (let [updated-variable (vu/save-variable token variable)]
;           (is (= {:status 200 :revision-id 2} (select-keys updated-variable [:status :revision-id])))
;           (index/wait-until-indexed)
;           (assert-variable-associated [latest-coll])))
;
;       (testing "Variable not associated with collection after deleting and recreating the variable"
;         (is (= {:status 200 :concept-id (:concept-id variable) :revision-id 3}
;                (vu/delete-variable token (:Name variable))))
;         (index/wait-until-indexed)
;
;         (testing "Not associated after variable deleted"
;           (assert-variable-not-associated))
;
;         (is (= {:status 200 :concept-id (:concept-id variable) :revision-id 4}
;                (vu/create-variable token (vu/make-variable {:Name "variable1"}))))
;         (index/wait-until-indexed)
;         (testing "Not associated after being recreated."
;           (assert-variable-not-associated))))))

(defn- assert-variable-association
  "Assert the collections are associated with the variable for the given variable-name"
  [token colls variable-name]
  (is (d/refs-match? colls
                     (search/find-refs :collection {:token token
                                                    :variable-name variable-name}))))

(deftest associate-dissociate-variable-with-collections-test
  ;; Grant all collections in PROV1
  (e/grant-registered-users (s/context) (e/coll-catalog-item-id "PROV1"))
  (let [[coll1 coll2 coll3] (for [n (range 1 4)]
                              (d/ingest "PROV1" (dc/collection)))
        [coll1-id coll2-id coll3-id] (map :concept-id [coll1 coll2 coll3])
        token (e/login (s/context) "user1")]
    (vu/create-variable token (vu/make-variable {:Name "variable1"}))
    (vu/create-variable token (vu/make-variable {:Name "variable2"}))
    (index/wait-until-indexed)

    ;; associate variable1 to coll1, variable2 to coll2
    ;; both :concept-id and :concept_id works as keys
    (vu/associate-by-concept-ids token "variable1" [{:concept_id coll1-id}])
    (vu/associate-by-concept-ids token "variable2" [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll1] "variable1")
    (assert-variable-association token [coll2] "variable2")

    ;; associate variable1 to coll1 again
    (vu/associate-by-concept-ids token "variable1" [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll1] "variable1")
    (assert-variable-association token [coll2] "variable2")

    ;; associate variable1 to coll2
    (vu/associate-by-concept-ids token "variable1" [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll1 coll2] "variable1")
    (assert-variable-association token [coll2] "variable2")

    ;; associate variable2 to coll1, coll2 and coll3
    (vu/associate-by-concept-ids token "variable2" [{:concept-id coll1-id}
                                                    {:concept-id coll2-id}
                                                    {:concept-id coll3-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll1 coll2] "variable1")
    (assert-variable-association token [coll1 coll2 coll3] "variable2")

    ;; dissociate variable1 from coll1
    (vu/dissociate-by-concept-ids token "variable1" [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll2] "variable1")
    (assert-variable-association token [coll1 coll2 coll3] "variable2")

    ;; dissociate variable2 from coll1 and coll2
    (vu/dissociate-by-concept-ids token "variable2" [{:concept-id coll1-id}
                                                     {:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll2] "variable1")
    (assert-variable-association token [coll3] "variable2")))
