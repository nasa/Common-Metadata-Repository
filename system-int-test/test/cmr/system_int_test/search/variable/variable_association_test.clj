(ns cmr.system-int-test.search.variable.variable-association-test
  "This tests associating variables with collections."
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.collection :as collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.association-util :as association-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as vu]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1"
                          "provguid2" "PROV2"
                          "provguid3" "PROV3"}
                         {:grant-all-search? false})
   vu/grant-all-variable-fixture]))

(deftest associate-variables-by-concept-ids-with-collections-test

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
         c1-p3 c2-p3 c3-p3 c4-p3] (doall (for [p ["PROV1" "PROV2" "PROV3"]
                                               n (range 1 5)]
                                           (:concept-id (data-core/ingest
                                                         p
                                                         (collection/collection
                                                          {:short-name (str "S" n)
                                                           :version-id (str "V" n)
                                                           :entry-title (str "ET" n)})))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        token (echo-util/login (system/context) "user1")
        {:keys [concept-id]} (vu/ingest-variable-with-attrs {:Name "variable1"})]
    (index/wait-until-indexed)

    (testing "Associate variable with collections by concept-ids"
      (let [response (association-util/associate-by-concept-ids
                      token concept-id [{:concept-id c1-p1}])]
        (vu/assert-variable-association-response-ok?
          {["C1200000013-PROV1"] {:concept-id "VA1200000026-CMR"
                                  :revision-id 1}}
          response)))

    (testing "Associate to no collections"
      (let [response (association-util/associate-by-concept-ids token concept-id [])]
        (association-util/assert-invalid-data-error
         ["At least one collection must be provided for variable association."]
         response)))

    (testing "Associate to collection revision and whole collection at the same time"
      (let [response (association-util/associate-by-concept-ids
                      token concept-id [{:concept-id c1-p1}
                                        {:concept-id c1-p1 :revision-id 1}])]
        (is (= 400 (:status response)))
        (is (= "Only one collection allowed in the list because a variable can only be associated with one collection."
               (:error response)))))

    (testing "Associate to non-existent collections"
      (let [response (association-util/associate-by-concept-ids
                      token concept-id [{:concept-id "C100-P5"}])]
        (vu/assert-variable-association-bad-request
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [response (association-util/associate-by-concept-ids token
                                                                concept-id
                                                                [{:concept-id c4-p3}])]
        (vu/assert-variable-association-bad-request
          {[c4-p3] {:errors [(format "Collection [%s] does not exist or is not visible." c4-p3)]}}
          response)))))

(deftest associate-variable-failure-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (let [native-id "var123"
        token (echo-util/login (system/context) "user1")
        var-concept (vu/make-variable-concept {:native-id native-id
                                               :Name "variable1"
                                               :provider-id "PROV1"})
        {:keys [concept-id revision-id]} (vu/ingest-variable var-concept)
        coll-concept-id (:concept-id (data-core/ingest "PROV1" (collection/collection)))]
    (testing "Associate variable using query sent with invalid content type"
      (are [associate-variable-fn request-json]
        (= {:status 400,
            :errors
            ["The mime types specified in the content-type header [application/xml] are not supported."]}
           (associate-variable-fn token concept-id request-json {:http-options {:content-type :xml}}))

        association-util/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate applies JSON Query validations"
      (are [associate-variable-fn request-json message]
        (= {:status 400
            :errors [message]}
           (associate-variable-fn token concept-id {:foo "bar"}))

        association-util/associate-by-concept-ids {:concept-id coll-concept-id}
        "#: expected type: JSONArray, found: JSONObject"))

    (testing "Associate variable that doesn't exist"
      (are [associate-variable-fn request-json]
        (= {:status 404
            :errors ["Variable could not be found with concept id [V12345-PROV1]"]}
           (associate-variable-fn token "V12345-PROV1" request-json))

        association-util/associate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Associate deleted variable"
      (ingest/delete-concept var-concept {:token token})
      (are [associate-variable-fn request-json]
        (= {:status 404
            :errors [(format "Variable with concept id [%s] was deleted." concept-id)]}
           (associate-variable-fn token concept-id request-json))

        association-util/associate-by-concept-ids [{:concept-id coll-concept-id}]))))

(deftest dissociate-variables-with-collections-by-concept-ids-test
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
         c1-p3 c2-p3 c3-p3 c4-p3] (doall (for [p ["PROV1" "PROV2" "PROV3"]
                                               n (range 1 5)]
                                           (data-core/ingest
                                            p
                                            (collection/collection
                                             {:short-name (str "S" n)
                                              :version-id (str "V" n)
                                              :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-prov3-colls [c1-p3 c2-p3 c3-p3 c4-p3]
        all-colls (concat all-prov1-colls all-prov2-colls all-prov3-colls)
        variable-name "variable1"
        token (echo-util/login (system/context) "user1")
        prov3-token (echo-util/login (system/context) "prov3-user" [group1-concept-id])
        {:keys [concept-id]} (vu/ingest-variable-with-attrs {:Name variable-name})
        assert-variable-associated (partial vu/assert-variable-associated-with-query
                                            prov3-token {:variable-name variable-name})]
    (index/wait-until-indexed)
    ;; Associate the variable with every prov1 collection is not allowed.
    ;; it can only be associated to one of the prov1 collections.
    (let [response1 (association-util/associate-by-concept-ids
                      prov3-token
                      concept-id
                      (map #(hash-map :concept-id (:concept-id %)) [c1-p1]))
          response2 (association-util/associate-by-concept-ids
                      prov3-token
                      concept-id
                      (map #(hash-map :concept-id (:concept-id %)) [c2-p1]))
          response3 (association-util/associate-by-concept-ids
                      prov3-token
                      concept-id
                      (map #(hash-map :concept-id (:concept-id %)) all-prov1-colls))]
       (is (= 200 (:status response1)))
       (is (= 200 (:status response2)))
       (is (nil? (:errors (first (:body response2)))))
       (is (= 400 (:status response3)))
       (is (= "Only one collection allowed in the list because a variable can only be associated with one collection."
              (:error response3))))
    ;; Associate the variable with every prov2 collection is not allowed.
    ;; it can not be associated with any because they are from different provider.
    (let [response (association-util/associate-by-concept-ids
                     prov3-token
                     concept-id
                     (map #(hash-map :concept-id (:concept-id %)) [c1-p2]))]
       (is (= 400 (:status response)))
       (is (string/includes? (:errors (first (:body response)))
                             "can not be associated because they do not belong to the same provider")))

    ;; only one prov1 collection can be associated with the variable.
    (assert-variable-associated [c2-p1])

    (testing "Dissociate non-existent collections"
      (let [response (association-util/dissociate-by-concept-ids
                      token concept-id [{:concept-id "C100-P5"}])]
        (vu/assert-variable-association-bad-request
         {["C100-P5"] {:errors ["Collection [C100-P5] does not exist or is not visible."]}}
         response)))

    (testing "Dissociate to deleted collections"
      (let [c1-p2-concept-id (:concept-id c1-p2)
            c1-p2-concept (mdb/get-concept c1-p2-concept-id)
            _ (ingest/delete-concept c1-p2-concept)
            _ (index/wait-until-indexed)
            response (association-util/dissociate-by-concept-ids
                      token concept-id [{:concept-id c1-p2-concept-id}])]
        (vu/assert-variable-association-bad-request
         {[c1-p2-concept-id] {:errors [(format "Collection [%s] does not exist or is not visible."
                                               c1-p2-concept-id)]}}
         response)))

    (testing "ACLs are applied to collections found"
      ;; None of PROV3's collections are visible
      (let [coll-concept-id (:concept-id c4-p3)
            response (association-util/dissociate-by-concept-ids
                      token concept-id [{:concept-id coll-concept-id}])]
        (vu/assert-variable-association-bad-request
         {[coll-concept-id] {:errors [(format "Collection [%s] does not exist or is not visible."
                                              coll-concept-id)]}}
         response)))))

(deftest dissociate-variable-failure-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (let [variable-name "variable1"
        token (echo-util/login (system/context) "user1")
        var-concept (vu/make-variable-concept {:Name variable-name})
        {:keys [concept-id revision-id]} (vu/ingest-variable var-concept)
        coll-concept-id (:concept-id (data-core/ingest
                                       "PROV1"
                                       (collection/collection)))
        [c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (doall (for [p ["PROV1" "PROV2" "PROV3"]
                                               n (range 1 5)]
                                           (data-core/ingest
                                             p
                                             (collection/collection
                                               {:short-name (str "S" n)
                                                :version-id (str "V" n)
                                                :entry-title (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]]

    (testing "Dissociate variable using query sent with invalid content type"
      (are [dissociate-variable-fn request-json]
        (= {:status 400,
            :errors
            ["The mime types specified in the content-type header [application/xml] are not supported."]}
           (dissociate-variable-fn token concept-id request-json {:http-options {:content-type :xml}}))

        association-util/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate applies JSON Query validations"
      (are [dissociate-variable-fn request-json message]
        (= {:status 400
            :errors [message]}
           (dissociate-variable-fn token concept-id request-json))

        association-util/dissociate-by-concept-ids {:concept-id coll-concept-id}
        "#: expected type: JSONArray, found: JSONObject"))

    (testing "Dissociate variable that doesn't exist"
      (are [dissociate-variable-fn request-json]
        (= {:status 404
            :errors ["Variable could not be found with concept id [V12345-PROV1]"]}
           (dissociate-variable-fn token "V12345-PROV1" request-json))

        association-util/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))

    (testing "Dissociate deleted variable"
      (ingest/delete-concept var-concept {:token token})
      (are [dissociate-variable-fn request-json]
        (= {:status 404
            :errors [(format "Variable with concept id [%s] was deleted." concept-id)]}
           (dissociate-variable-fn token concept-id request-json))

        association-util/dissociate-by-concept-ids [{:concept-id coll-concept-id}]))
    
    (testing "Dissociate multiple variables"
      (let [{:keys [status error]} (association-util/dissociate-by-concept-ids
                                     token
                                     concept-id
                                     (map #(hash-map :concept-id (:concept-id %)) all-prov1-colls))]
        (is (= 400 status))
        (is (= "Only one variable at a time may be dissociated." error))))))

;; This tests association retention when collections and variables are updated or deleted.
(deftest association-retention-test
  (echo-util/grant-all (system/context) (echo-util/coll-catalog-item-id "PROV1"))
  (let [token (echo-util/login (system/context) "user1")
        coll (data-core/ingest "PROV1" (collection/collection))
        var-concept (vu/make-variable-concept {:native-id "var123"
                                               :Name "variable1"})
        {:keys [concept-id]} (vu/ingest-variable var-concept)
        _ (index/wait-until-indexed)
        _ (association-util/associate-by-concept-ids token
                                                     concept-id
                                                     [{:concept-id (:concept-id coll)}])
        assert-variable-associated (partial vu/assert-variable-associated-with-query
                                            nil {:variable-name "variable1"})
        assert-variable-not-associated (fn []
                                         (let [refs (search/find-refs
                                                     :collection {:variable-name "variable1"})]
                                           (is (nil? (:errors refs)))
                                           (is (data-core/refs-match? [] refs))))]
    (index/wait-until-indexed)

    (testing "Variable initially associated with collection"
      (assert-variable-associated [coll]))

    (testing "Variable still associated with collection after updating collection"
      (let [updated-coll (data-core/ingest "PROV1" (dissoc coll :revision-id))]
        (is (= 200 (:status updated-coll)))
        (index/wait-until-indexed)
        (assert-variable-associated [updated-coll])))

    (testing "Variable no longer associated with collection after deleting and recreating the collection"
      (is (= 200 (:status (ingest/delete-concept (data-core/item->concept coll)))))
      (let [update-resp (data-core/ingest "PROV1" (dissoc coll :revision-id))]
        (is (= 200 (:status update-resp)))
        (index/wait-until-indexed)
        (assert-variable-not-associated)))

    ;; create the association again
    (let [latest-coll (assoc coll :revision-id 4)]
      (association-util/associate-by-concept-ids token
                                                concept-id
                                                [{:concept-id (:concept-id latest-coll)}])
      ;; The association above fails because variable with concept-id was deleted when the
      ;; collection was deleted.
      (assert-variable-associated [])

      (testing "Variable still associated with collection after updating variable"
        (let [updated-variable (vu/ingest-variable var-concept)]
          ;; when the collection was deleted, the variable was deleted and the revision-id
          ;; was 2, now ingest again, it's 3.
          (is (= {:status 200 :concept-id concept-id :revision-id 3}
                 (select-keys updated-variable [:status :concept-id :revision-id])))
          (index/wait-until-indexed)
          ;; there wasn't association created.
          (assert-variable-associated [])))

      (testing "Variable not associated with collection after deleting and recreating the variable"
        (is (= {:status 200 :concept-id concept-id :revision-id 4}
               (select-keys (ingest/delete-concept var-concept {:token token})
                            [:status :concept-id :revision-id])))
        (index/wait-until-indexed)

        (testing "Not associated after variable deleted"
          (assert-variable-not-associated))

        (testing "Not associated after variable is recreated."
          ;; create a new revision of the variable
          (is (= {:status 200 :concept-id concept-id :revision-id 5}
                 (select-keys (vu/ingest-variable var-concept) [:status :concept-id :revision-id])))
          (index/wait-until-indexed)

          (assert-variable-not-associated))))))

(defn- assert-variable-association
  "Assert the collections are associated with the variable for the given variable-name"
  [token colls variable-name]
  (is (data-core/refs-match? colls
                             (search/find-refs :collection {:token token
                                                            :variable-name variable-name}))))

(deftest associate-dissociate-variable-with-collections-test
  ;; Grant all collections in PROV1
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (let [[coll1 coll2 coll3] (doall (for [n (range 1 4)]
                                    (data-core/ingest "PROV1" (collection/collection))))
        [coll1-id coll2-id coll3-id] (map :concept-id [coll1 coll2 coll3])
        token (echo-util/login (system/context) "user1")
        {variable1-concept-id :concept-id} (vu/ingest-variable-with-attrs {:Name "variable1"})
        {variable2-concept-id :concept-id} (vu/ingest-variable-with-attrs {:Name "variable2"})]
    (index/wait-until-indexed)

    ;; associate variable1 to coll1, variable2 to coll2
    ;; both :concept-id and :concept_id works as keys
    (association-util/associate-by-concept-ids token
                                               variable1-concept-id
                                               [{:concept_id coll1-id}])
    (association-util/associate-by-concept-ids token
                                               variable2-concept-id
                                               [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll1] "variable1")
    (assert-variable-association token [coll2] "variable2")

    ;; associate variable1 to coll1 again
    (association-util/associate-by-concept-ids token
                                               variable1-concept-id
                                               [{:concept-id coll1-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll1] "variable1")
    (assert-variable-association token [coll2] "variable2")

    ;; associate variable1 to coll2
    (association-util/associate-by-concept-ids token
                                               variable1-concept-id
                                               [{:concept-id coll2-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll2] "variable1")
    (assert-variable-association token [coll2] "variable2")

    ;; associate variable2 to coll1, coll2 and coll3
    (association-util/associate-by-concept-ids token
                                               variable2-concept-id
                                               [{:concept-id coll1-id}])
    (association-util/associate-by-concept-ids token
                                               variable2-concept-id
                                               [{:concept-id coll2-id}])
    (association-util/associate-by-concept-ids token
                                               variable2-concept-id
                                               [{:concept-id coll3-id}])
    (index/wait-until-indexed)
    ;; verify association
    (assert-variable-association token [coll2] "variable1")
    (assert-variable-association token [coll3] "variable2")
    ))

(deftest single-association-route-test
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV1"))
  (echo-util/grant-registered-users (system/context)
                                    (echo-util/coll-catalog-item-id "PROV2"))

  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2
         c1-p3 c2-p3 c3-p3 c4-p3] (doall (for [p ["PROV1" "PROV2" "PROV3"]
                                               n (range 1 5)]
                                           (:concept-id (data-core/ingest
                                                          p
                                                          (collection/collection
                                                            {:short-name (str "S" n)
                                                             :version-id (str "V" n)
                                                             :entry-title (str "ET" n)})))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        token (echo-util/login (system/context) "user1")
        {:keys [concept-id]} (vu/ingest-variable-with-attrs {:Name "variable1"})]
    (index/wait-until-indexed)

    (testing "variable to collection"
      (testing "association should succeed"
        (let [response (association-util/associate-by-single-concept-id
                         token concept-id c1-p1)]
          (vu/assert-variable-association-response-ok?
            {["C1200000013-PROV1"] {:concept-id "VA1200000026-CMR"
                                    :revision-id 1}}
            response))))))
