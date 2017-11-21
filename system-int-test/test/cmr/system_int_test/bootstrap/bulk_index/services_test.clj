(ns cmr.system-int-test.bootstrap.bulk-index.services-test
  "Integration test for CMR bulk index service operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest bulk-index-services-for-provider
  (testing "Bulk index services for a single provider"
    (s/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following is saved, but not indexed due to the above call
     (let [svc1 (service/ingest-service-with-attrs {:provider-id "PROV1"} {} 1)
           ;; create a service on a different provider PROV2
           ;; and this service won't be indexed as a result of indexing services of PROV1
           svc2 (service/ingest-service-with-attrs {:provider-id "PROV2"} {} 1)]
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

(deftest bulk-index-services
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

(deftest bulk-index-all-services
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
