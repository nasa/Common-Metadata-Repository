(ns cmr.system-int-test.search.tool.tool-association-permission-test
  "This tests associating tools with collections."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.tool-util :as tool-util]
   [cmr.system-int-test.utils.association-util :as association-util]))

(use-fixtures
 :each
 (ingest/reset-fixture-with-customized-options {"provguid1" "PROV1"
                                                "provguid2" "PROV2"}
                                                {"PROV1" {:grant-all-search? false :grant-all-ingest? false}
                                                 "PROV2" {:grant-all-search? false :grant-all-ingest? true}}))


(deftest service-association-permission-test
  ;; Create two collections and tools on PROV1 and PROV2
  ;; PROV1 has :grant-all-ingest? set to false, meaning no one has update permission on INGEST_MANAGEMENT_ACL.
  ;; PROV2 has :grant-all-ingest? set to true, meaning everyone has update permission on INGEST_MANAGEMENT_ACL.
  
  ;; Even though guest has update permission on INGEST_MANAGEMENT_ACL for PROV2, we still need to 
  ;; Make sure all the collections on PROV2 are visible to guest.
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
        ;; create a guest token to be used for association.
        token (echo-util/login-guest (system/context))
        _ (index/wait-until-indexed)
       
        ;; token has no permission to associate tool1 with coll1.
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

    (is (= {:status 400, :body [{:errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] to make the association.")], :associated-item {:concept-id coll1-concept-id}} {:tool-association {:concept-id tla1-concept-id, :revision-id 1}, :associated-item {:concept-id coll2-concept-id, :revision-id coll2-revision-id}}]}
           assoc-response1))

    (is (= {:status 400, :body [{:errors [(str "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [" coll1-concept-id "] to make the association.")], :associated-item {:concept-id coll1-concept-id}} {:tool-association {:concept-id tla2-concept-id, :revision-id 1}, :associated-item {:concept-id coll2-concept-id, :revision-id coll2-revision-id}}]}
           assoc-response2))))
