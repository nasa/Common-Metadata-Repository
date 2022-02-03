(ns cmr.system-int-test.bootstrap.bulk-index.services-test
  "Integration test for CMR bulk index service operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest ^:oracle bulk-index-services-for-provider
  (testing "Bulk index services for a single provider"
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following is saved, but not indexed due to the above call
     (let [svc1 (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 1)
           ;; create a service on a different provider PROV2
           ;; and this service won't be indexed as a result of indexing services of PROV1
           svc2 (service/ingest-service-with-attrs {:provider-id "PROV2"} {} 1)
           {:keys [status errors]} (bootstrap/bulk-index-services "PROV1" nil)]

       (is (= [401 ["You do not have permission to perform that action."]]
              [status errors]))
       (is (= 0 (:hits (search/find-refs :service {}))))
       (bootstrap/bulk-index-services "PROV1")
       (index/wait-until-indexed)
       (testing "Service concepts are indexed."
         (let [{:keys [hits refs]} (search/find-refs :service {})]
           (is (= 1 hits))
           (is (= (:concept-id svc1)
                  (:id (first refs)))))))
     (testing "Bulk index multilpe services for a single provider")
     (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 2)
     (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 3)
     (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 4)
     (is (= 1 (:hits (search/find-refs :service {}))))
     (bootstrap/bulk-index-services "PROV1")
     (index/wait-until-indexed)
     (let [{:keys [hits refs]} (search/find-refs :service {})]
       (is (= 4 hits))
       (is (= 4 (count refs))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-services
  (testing "Bulk index services for multiple providers, explicitly"
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 1)
     (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 2)
     (service/ingest-service-with-attrs {:provider-id "PROV2"} {} 3)
     (service/ingest-service-with-attrs {:provider-id "PROV2"} {} 4)
     (service/ingest-service-with-attrs {:provider-id "PROV3"} {} 5)
     (service/ingest-service-with-attrs {:provider-id "PROV3"} {} 6)
     (is (= 0 (:hits (search/find-refs :service {}))))
     (bootstrap/bulk-index-services "PROV1")
     (bootstrap/bulk-index-services "PROV2")
     (bootstrap/bulk-index-services "PROV3")
     (index/wait-until-indexed)
     (testing "Service concepts are indexed."
       (let [{:keys [hits refs] :as response} (search/find-refs :service {})]
         (is (= 6 hits))
         (is (= 6 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-all-services
  (testing "Bulk index services for multiple providers, implicitly"
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 1)
     (service/ingest-service-with-attrs {:provider-id "PROV2"} {} 2)
     (service/ingest-service-with-attrs {:provider-id "PROV3"} {} 3)
     (is (= 0 (:hits (search/find-refs :service {}))))
     (bootstrap/bulk-index-services)
     (index/wait-until-indexed)
     (testing "Service concepts are indexed."
       (let [{:keys [hits refs]} (search/find-refs :service {})]
         (is (= 3 hits))
         (is (= 3 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-service-revisions
  (testing "Bulk index services index all revisions index as well"
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     (let [token (e/login (s/context) "user1")
           svc1-concept (service/make-service-concept {:native-id "SVC1"
                                                       :Name "Service1"
                                                       :provider-id "PROV1"})
           svc2-concept (service/make-service-concept {:native-id "SVC2"
                                                       :Name "Service2"
                                                       :provider-id "PROV2"})
           svc2-2-concept (service/make-service-concept {:native-id "SVC2"
                                                         :Name "Service2-2"
                                                         :provider-id "PROV2"})
           svc3-concept (service/make-service-concept {:native-id "SVC3"
                                                       :Name "Service1"
                                                       :provider-id "PROV3"})
           svc1-1 (service/ingest-service svc1-concept)
           svc1-2-tombstone (merge (ingest/delete-concept
                                    svc1-concept (service/token-opts token))
                                  svc1-concept
                                   {:deleted true
                                    :user-id "user1"})
           svc1-3 (service/ingest-service svc1-concept)
           svc2-1 (service/ingest-service svc2-concept)
           svc2-2 (service/ingest-service svc2-2-concept)
           svc2-3-tombstone (merge (ingest/delete-concept
                                    svc2-2-concept (service/token-opts token))
                                   svc2-2-concept
                                   {:deleted true
                                    :user-id "user1"})
           svc3 (service/ingest-service svc3-concept)]
       ;; Before bulk indexing, search for services found nothing
       (du/assert-service-umm-jsons-match
        umm-version/current-service-version
        []
        (search/find-concepts-umm-json :service {:all-revisions true}))

       ;; Just index PROV1
       (bootstrap/bulk-index-services "PROV1")
       (index/wait-until-indexed)

       ;; After bulk indexing a provider, search found all service revisions
       ;; of that provider
       (du/assert-service-umm-jsons-match
        umm-version/current-service-version
        [svc1-1 svc1-2-tombstone svc1-3]
        (search/find-concepts-umm-json :service {:all-revisions true}))

       ;; Now index all services
       (bootstrap/bulk-index-services)
       (index/wait-until-indexed)

       ;; After bulk indexing, search for services found all revisions
       (du/assert-service-umm-jsons-match
        umm-version/current-service-version
        [svc1-1 svc1-2-tombstone svc1-3 svc2-1 svc2-2 svc2-3-tombstone svc3]
        (search/find-concepts-umm-json :service {:all-revisions true}))

       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))

(deftest ^:oracle bulk-index-collections-with-service-association-test
  (s/only-with-real-database
   (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"}))
         coll1-concept-id (:concept-id coll1)
         token (e/login (s/context) "user1")
         {serv1-concept-id :concept-id} (service/ingest-service-with-attrs
                                          {:native-id "serv1"
                                           :Name "service1"
                                           :Type "ESI"
                                           :ServiceOptions {:InterpolationTypes ["Bilinear Interpolation"]}})
         {serv2-concept-id :concept-id} (service/ingest-service-with-attrs
                                          {:native-id "serv2"
                                           :Name "service2"})]
     ;; index the collection and services so that they can be found during service association
     (index/wait-until-indexed)

     (core/disable-automatic-indexing)
     (au/associate-by-concept-ids token serv1-concept-id [{:concept-id coll1-concept-id}])
     ;; service 2 is used to test service association tombstone is indexed correctly
     (au/associate-by-concept-ids token serv2-concept-id [{:concept-id coll1-concept-id}])
     (au/dissociate-by-concept-ids token serv2-concept-id [{:concept-id coll1-concept-id}])
     (core/reenable-automatic-indexing)

     ;; bulk index the collection
     (bootstrap/bulk-index-provider "PROV1")
     (index/wait-until-indexed)

     ;; verify collection is associated with service1, not service2
     (service/assert-collection-search-result
       coll1
       {:has-transforms true
        :service-features {:esi {:has-transforms true}}}
       [serv1-concept-id]))))
