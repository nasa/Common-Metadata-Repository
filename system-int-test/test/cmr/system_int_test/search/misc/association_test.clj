(ns cmr.system-int-test.search.misc.association-test
  "This tests associating tools and services with collections."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cmr.mock-echo.client.echo-util :as echo-util]
            [cmr.system-int-test.data2.core :as data-core]
            [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
            [cmr.system-int-test.system :as system]
            [cmr.system-int-test.utils.association-util :as association-util]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.service-util :as service-util]
            [cmr.system-int-test.utils.tool-util :as tool-util]))

(use-fixtures
  :once
  (ingest/reset-fixture-with-customized-options {"provguid1" "PROV1"
                                                 "provguid2" "PROV2"}
                                                {"PROV1" {:grant-all-search? false :grant-all-ingest? false}
                                                 "PROV2" {:grant-all-search? false :grant-all-ingest? true}}))

(deftest association-tests
  ;; Create two collections and tools on PROV1 and PROV2
  ;; PROV1 has :grant-all-ingest? set to false, meaning no one has update permission on INGEST_MANAGEMENT_ACL.
  ;; PROV2 has :grant-all-ingest? set to true, meaning everyone has update permission on INGEST_MANAGEMENT_ACL.

  ;; Make sure all the collections on PROV1 and PROV2 are visible to guest.
  (echo-util/grant-guest (system/context) (echo-util/coll-catalog-item-id "PROV1"))
  (echo-util/grant-guest (system/context) (echo-util/coll-catalog-item-id "PROV2"))
  (let [coll1 (data-core/ingest-umm-spec-collection "PROV1"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll1"
                                                      :EntryTitle "entry-title1"})
                                                    {:token "mock-echo-system-token"})
        coll2 (data-core/ingest-umm-spec-collection "PROV2"
                                                    (data-umm-c/collection
                                                     {:ShortName "coll2"
                                                      :EntryTitle "entry-title2"}))
        coll1-concept-id (:concept-id coll1)
        coll2-concept-id (:concept-id coll2)
        coll2-revision-id (:revision-id coll2)
        tool1-concept (tool-util/make-tool-concept {:native-id "tool1"
                                                    :Name "Tool1"
                                                    :provider-id "PROV1"})
        tool2-concept (tool-util/make-tool-concept {:native-id "tool2"
                                                    :Name "Tool2"
                                                    :provider-id "PROV2"})
        tool1 (tool-util/ingest-tool tool1-concept {:token "mock-echo-system-token"})
        tool2 (tool-util/ingest-tool tool2-concept)

        svc1-concept (service-util/make-service-concept {:native-id "svc1"
                                                         :Name "Service1"
                                                         :provider-id "PROV1"})
        svc2-concept (service-util/make-service-concept {:native-id "svc2"
                                                         :Name "Service2"
                                                         :provider-id "PROV2"})
        serv1 (service-util/ingest-service svc1-concept {:token "mock-echo-system-token"})
        serv2 (service-util/ingest-service svc2-concept)
        ;; create a guest token to be used for association.
        token (echo-util/login-guest (system/context))]
    (index/wait-until-indexed)

    (testing "tool-association-permission-test"
      (let [;; token has no permission to associate tool1 with coll1.
            ;; token has permission to associate tool1 with coll2.
            assoc-response1 (association-util/associate-by-concept-ids token
                                                                       (:concept-id tool1)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id
                                                                         :revision-id coll2-revision-id}])
            tla1-concept-id (some #(when (:tool-association %) (get-in % [:tool-association :concept-id]))
                                  (:body assoc-response1))
            ;; token has no permission to associate tool2 with coll1.
            ;; token has permission to associate tool2 with coll2.  
            assoc-response2 (association-util/associate-by-concept-ids token
                                                                       (:concept-id tool2)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id
                                                                         :revision-id coll2-revision-id}])
            tla2-concept-id (some #(when (:tool-association %) (get-in % [:tool-association :concept-id]))
                                  (:body assoc-response2))]
        (is (= {:status 207, :body [{:status 400
                                     :errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] to make the association.")],
                                     :associated-item {:concept-id coll1-concept-id}}
                                    {:status 200,
                                     :tool-association {:concept-id tla1-concept-id, :revision-id 1},
                                     :associated-item {:concept-id coll2-concept-id, :revision-id coll2-revision-id}}]}
               assoc-response1))
 
        (is (= {:status 207, :body [{:status 400
                                     :errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] to make the association.")],
                                     :associated-item {:concept-id coll1-concept-id}}
                                    {:status 200
                                     :tool-association {:concept-id tla2-concept-id, :revision-id 1},
                                     :associated-item {:concept-id coll2-concept-id, :revision-id coll2-revision-id}}]}
               assoc-response2))))

    (testing "dissociation-permission-test"
      (let [;; make all 4 associations using mock-echo-system-token 
            assoc-response1 (association-util/associate-by-concept-ids "mock-echo-system-token"
                                                                       (:concept-id tool1)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id
                                                                         :revision-id coll2-revision-id}])
            assoc-response2 (association-util/associate-by-concept-ids "mock-echo-system-token"
                                                                       (:concept-id tool2)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id
                                                                         :revision-id coll2-revision-id}])
            tla1-concept-ids (map #(when (:tool-association %) (get-in % [:tool-association :concept-id]))
                                  (:body assoc-response1))
            tla2-concept-ids (map #(when (:tool-association %) (get-in % [:tool-association :concept-id]))
                                  (:body assoc-response2))
            ;; create a guest token to be used for dissociation.
            token (echo-util/login-guest (system/context))

            ;; dissoc 4 associations using guest token
            ;; token has no permission to dissoc tool1 from coll1
            ;; token has permission to dissoc tool1 from coll2
            dissoc-response1 (association-util/dissociate-by-concept-ids token
                                                                         (:concept-id tool1)
                                                                         [{:concept-id coll1-concept-id}
                                                                          {:concept-id coll2-concept-id
                                                                           :revision-id coll2-revision-id}])
            ;; token has permission to dissoc tool2 from coll1
            ;; token has permission to dissoc tool2 from coll2
            dissoc-response2 (association-util/dissociate-by-concept-ids token
                                                                         (:concept-id tool2)
                                                                         [{:concept-id coll1-concept-id}
                                                                          {:concept-id coll2-concept-id
                                                                           :revision-id coll2-revision-id}])]
        (is (= {:status 207, :body [{:status 400
                                     :errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] or provider of service/tool to delete the association.")],
                                     :associated-item {:concept-id coll1-concept-id}}
                                    {:status 200
                                     :tool-association {:concept-id (last tla1-concept-ids), :revision-id 3},
                                     :associated-item {:concept-id coll2-concept-id, :revision-id 1}}]}
               dissoc-response1))

        (is (= {:status 200, :body [{:tool-association {:concept-id (first tla2-concept-ids), :revision-id 2},
                                     :associated-item {:concept-id coll1-concept-id}}
                                    {:tool-association {:concept-id (last tla2-concept-ids), :revision-id 3},
                                     :associated-item {:concept-id coll2-concept-id, :revision-id 1}}]}
               dissoc-response2))))

    (testing "service-association-permission-test"
      (let [;; token has no permission to associate serv1 with coll1.
            ;; token has permission to associate serv1 with coll2.
            assoc-response1 (association-util/associate-by-concept-ids token
                                                                       (:concept-id serv1)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id 
                                                                         :revision-id coll2-revision-id}])
            sa1-concept-id (some #(when (:service-association %) (get-in % [:service-association :concept-id]))
                                 (:body assoc-response1))
            ;; token has no permission to associate serv2 with coll1.
            ;; token has permission to associate serv2 with coll2
            assoc-response2 (association-util/associate-by-concept-ids token
                                                                       (:concept-id serv2)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id 
                                                                         :revision-id coll2-revision-id}])
             sa2-concept-id (some #(when (:service-association %) (get-in % [:service-association :concept-id]))
                                  (:body assoc-response2))]
        (is (= {:status 207
                :body [{:status 400
                        :errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] to make the association.")]
                        :associated-item {:concept-id coll1-concept-id}}
                       {:status 200
                        :service-association {:concept-id sa1-concept-id, :revision-id 1}
                        :associated-item {:concept-id coll2-concept-id, :revision-id coll2-revision-id}}]}
               assoc-response1))

        (is (= {:status 207
                :body [{:status 400
                        :errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] to make the association.")]
                        :associated-item {:concept-id coll1-concept-id}}
                       {:status 200
                        :service-association {:concept-id sa2-concept-id, :revision-id 1}
                        :associated-item {:concept-id coll2-concept-id, :revision-id coll2-revision-id}}]}
               assoc-response2))))

    (testing "service-dissociation-permission-test"
      (let [;; make all 4 associations using mock-echo-system-token
            assoc-response1 (association-util/associate-by-concept-ids "mock-echo-system-token" 
                                                                       (:concept-id serv1)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id
                                                                         :revision-id coll2-revision-id}])
            assoc-response2 (association-util/associate-by-concept-ids "mock-echo-system-token" 
                                                                       (:concept-id serv2)
                                                                       [{:concept-id coll1-concept-id}
                                                                        {:concept-id coll2-concept-id
                                                                         :revision-id coll2-revision-id}])
            sa1-concept-ids (map #(when (:service-association %) (get-in % [:service-association :concept-id]))
                                 (:body assoc-response1))
            sa2-concept-ids (map #(when (:service-association %) (get-in % [:service-association :concept-id]))
                                 (:body assoc-response2))
            ;; create a guest token to be used for dissociation.
            token (echo-util/login-guest (system/context))
        
            ;; dissoc 4 associations using guest token
            ;; token has no permission to dissoc serv1 from coll1
            ;; token has permission to dissoc serv1 from coll2 
            dissoc-response1 (association-util/dissociate-by-concept-ids token 
                                                                         (:concept-id serv1)
                                                                         [{:concept-id coll1-concept-id}
                                                                          {:concept-id coll2-concept-id
                                                                           :revision-id coll2-revision-id}])
            ;; token has permission to dissoc serv2 from coll1
            ;; token has permission to dissoc serv2 from coll2
            dissoc-response2 (association-util/dissociate-by-concept-ids token
                                                                         (:concept-id serv2)
                                                                         [{:concept-id coll1-concept-id}
                                                                          {:concept-id coll2-concept-id
                                                                           :revision-id coll2-revision-id}])]
        (is (= {:status 207
                :body [{:status 400
                        :errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] or provider of service/tool to delete the association.")]
                        :associated-item {:concept-id coll1-concept-id}} 
                       {:status 200
                        :service-association {:concept-id (last sa1-concept-ids), :revision-id 3}
                        :associated-item {:concept-id coll2-concept-id, :revision-id 1}}]}
               dissoc-response1))

        (is (= {:status 200
                :body [{:service-association {:concept-id (first sa2-concept-ids), :revision-id 2}
                        :associated-item {:concept-id coll1-concept-id}}
                       {:service-association {:concept-id (last sa2-concept-ids), :revision-id 3}
                        :associated-item {:concept-id coll2-concept-id, :revision-id 1}}]}
               dissoc-response2))))))
