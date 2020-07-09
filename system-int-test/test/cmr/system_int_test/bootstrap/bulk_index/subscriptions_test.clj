(ns cmr.system-int-test.bootstrap.bulk-index.subscriptions-test
  "Integration test for CMR bulk index subscription operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.umm-json :as data-umm-json]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.subscription-util :as subscription]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})
                      (subscription/grant-all-subscription-fixture
                        {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "PROV3"}
                        [:read :update]
                        [:read :update])]))

(deftest bulk-index-subscriptions-for-provider
  (testing "Bulk index subscriptions for a single provider"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following is saved, but not indexed due to the above call
     (let [sub1 (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 1)
           ;; create a subscription on a different provider PROV2
           ;; and this subscription won't be indexed as a result of indexing subscriptions of PROV1
           sub2 (subscription/ingest-subscription-with-attrs {:provider-id "PROV2"} {} 1)]
     
       ;; no index, no hits. 
       (is (= 0 (:hits (search/find-refs :subscription {}))))       
 
       (bootstrap/bulk-index-subscriptions "PROV1")
       (index/wait-until-indexed)

       (testing "Subscription concepts are indexed."
         ;; Note: only sub1 is indexed, sub2 is not.
         (let [{:keys [hits refs]} (search/find-refs :subscription {})]
           (is (= 1 hits))
           (is (= (:concept-id sub1)
                  (:id (first refs))))))

       (testing "Bulk index multilpe subscriptions for a single provider"
         ;; Ingest three more subscriptions
         (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 2)
         (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 3)
         (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 4)
         
         ;; The above three new subscriptions are not indexed, only sub1 is indexed. 
         (is (= 1 (:hits (search/find-refs :subscription {}))))
    
         ;; bulk index again, using system token, all the subscriptions in PROV1 should be indexed. 
         (bootstrap/bulk-index-subscriptions "PROV1")
         (index/wait-until-indexed)
     
         (let [{:keys [hits refs]} (search/find-refs :subscription {})]
           (is (= 4 hits))
           (is (= 4 (count refs))))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest bulk-index-subscriptions
  (testing "Bulk index subscriptions for multiple providers, explicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 1)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 2)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV2"} {} 3)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV2"} {} 4)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV3"} {} 5)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV3"} {} 6)

     (is (= 0 (:hits (search/find-refs :subscription {}))))

     (bootstrap/bulk-index-subscriptions "PROV1")
     (bootstrap/bulk-index-subscriptions "PROV2")
     (bootstrap/bulk-index-subscriptions "PROV3")
     (index/wait-until-indexed)

     (testing "Subscription concepts are indexed."
       (let [{:keys [hits refs] :as response} (search/find-refs :subscription {})]
         (is (= 6 hits))
         (is (= 6 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest bulk-index-all-subscriptions
  (testing "Bulk index subscriptions for multiple providers, implicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV1"} {} 1)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV2"} {} 2)
     (subscription/ingest-subscription-with-attrs {:provider-id "PROV3"} {} 3)

     (is (= 0 (:hits (search/find-refs :subscription {}))))

     (bootstrap/bulk-index-subscriptions)
     (index/wait-until-indexed)

     (testing "Subscription concepts are indexed."
       (let [{:keys [hits refs]} (search/find-refs :subscription {})]
         (is (= 3 hits))
         (is (= 3 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest bulk-index-subscription-revisions
  (testing "Bulk index subscriptions index all revisions index as well"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     (let [token (echo-util/login (system/context) "user1")
           sub1-concept (subscription/make-subscription-concept {:native-id "SUB1"
                                                                 :Name "Sub1"
                                                                 :provider-id "PROV1"})
           sub2-concept (subscription/make-subscription-concept {:native-id "SUB2"
                                                                 :Name "Sub2"
                                                                 :provider-id "PROV2"})
           sub2-2-concept (subscription/make-subscription-concept {:native-id "SUB2"
                                                                   :Name "Sub2-2"
                                                                   :provider-id "PROV2"})
           sub3-concept (subscription/make-subscription-concept {:native-id "SUB3"
                                                                 :Name "Sub1"
                                                                 :provider-id "PROV3"})
           sub1-1 (subscription/ingest-subscription sub1-concept)
           sub1-2-tombstone (merge (ingest/delete-concept
                                     sub1-concept (subscription/token-opts token))
                                   sub1-concept
                                   {:deleted true
                                    :user-id "user1"})
           sub1-3 (subscription/ingest-subscription sub1-concept)
           sub2-1 (subscription/ingest-subscription sub2-concept)
           sub2-2 (subscription/ingest-subscription sub2-2-concept)
           sub2-3-tombstone (merge (ingest/delete-concept
                                     sub2-2-concept (subscription/token-opts token))
                                   sub2-2-concept
                                   {:deleted true
                                    :user-id "user1"})
           sub3 (subscription/ingest-subscription sub3-concept)]
       ;; Before bulk indexing, search for services found nothing
       (data-umm-json/assert-subscription-umm-jsons-match
        umm-version/current-subscription-version
        []
        (search/find-concepts-umm-json :subscription {:all-revisions true}))

       ;; Just index PROV1
       (bootstrap/bulk-index-subscriptions "PROV1")
       (index/wait-until-indexed)

       ;; After bulk indexing a provider, search found all subscription revisions for the provider
       ;; of that provider
       (data-umm-json/assert-subscription-umm-jsons-match
        umm-version/current-subscription-version
        [sub1-1 sub1-2-tombstone sub1-3]
        (search/find-concepts-umm-json :subscription {:all-revisions true}))

       ;; Now index all subscriptions
       (bootstrap/bulk-index-subscriptions)
       (index/wait-until-indexed)

       ;; After bulk indexing, search for subscriptions found all revisions
       (data-umm-json/assert-subscription-umm-jsons-match
        umm-version/current-subscription-version
        [sub1-1 sub1-2-tombstone sub1-3 sub2-1 sub2-2 sub2-3-tombstone sub3]
        (search/find-concepts-umm-json :subscription {:all-revisions true}))

       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))
