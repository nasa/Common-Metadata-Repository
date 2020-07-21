(ns cmr.system-int-test.search.service.service-revisions-search-test
  "Integration test for service all revisions search"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                service/grant-all-service-fixture]))

(deftest search-service-all-revisions-after-cleanup
  (let [service1 {:native-id "SVC1"
                  :Name "Service1"
                  :provider-id "PROV1"}
        service2 {:native-id "SVC2"
                  :Name "Service2"
                  :provider-id "PROV2"}
        service1s (doall (for [n (range 12)]
                           (service/ingest-service
                             (service/make-service-concept service1))))
        service2s (doall (for [n (range 10)]
                        (service/ingest-service
                          (service/make-service-concept service2))))
        all-services-after-cleanup (concat (drop 2 service1s) service2s)]
    (index/wait-until-indexed)

    (is (= 204 (:status (mdb/cleanup-old-revisions))))
    (index/wait-until-indexed)

    (du/assert-service-umm-jsons-match
      umm-version/current-service-version
      all-services-after-cleanup
      (search/find-concepts-umm-json :service {:all-revisions true
                                               :page-size 20}))

    (d/assert-refs-match
      all-services-after-cleanup
      (search/find-refs :service {:all-revisions true
                                  :page-size 20}))))

(deftest search-service-all-revisions
  (let [token (e/login (s/context) "user1")
        svc1-concept (service/make-service-concept {:native-id "SVC1"
                                                    :Name "Service1"
                                                    :provider-id "PROV1"})
        svc2-concept (service/make-service-concept {:native-id "SVC2"
                                                    :Name "Service2"
                                                    :provider-id "PROV1"})
        svc2-2-concept (service/make-service-concept {:native-id "SVC2"
                                                      :Name "Service2-2"
                                                      :provider-id "PROV1"})
        svc3-concept (service/make-service-concept {:native-id "SVC3"
                                                    :Name "Service1"
                                                    :provider-id "PROV2"})
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
    (index/wait-until-indexed)
    (testing "search services for all revisions"
      (are3 [services params]
        (do
          ;; find references with all revisions
          (d/assert-refs-match
           services (service/search-refs params))
          ;; search in JSON with all-revisions
          (service/assert-service-search services (service/search-json params))
          ;; search in UMM JSON with all-revisions
          (du/assert-service-umm-jsons-match
           umm-version/current-service-version
           services
           (search/find-concepts-umm-json :service params)))

        "provider-id all-revisions=false"
        [svc1-3]
        {:provider-id "PROV1" :all-revisions false}

        "provider-id all-revisions unspecified"
        [svc1-3]
        {:provider-id "PROV1"}

        "provider-id all-revisions=true"
        [svc1-1 svc1-2-tombstone svc1-3 svc2-1 svc2-2 svc2-3-tombstone]
        {:provider-id "PROV1" :all-revisions true}

        "native-id all-revisions=false"
        [svc1-3]
        {:native-id "svc1" :all-revisions false}

        "native-id all-revisions unspecified"
        [svc1-3]
        {:native-id "svc1"}

        "native-id all-revisions=true"
        [svc1-1 svc1-2-tombstone svc1-3]
        {:native-id "svc1" :all-revisions true}

        ;; this test is across providers
        "name all-revisions false"
        [svc1-3 svc3]
        {:name "Service1" :all-revisions false}

        ;; this test is across providers
        "name all-revisions unspecified"
        [svc1-3 svc3]
        {:name "Service1"}

        "name all-revisions true"
        [svc1-1 svc1-2-tombstone svc1-3 svc3]
        {:name "Service1" :all-revisions true}

        "name is updated on revision -- not found without all-revisions true"
        []
        {:name "Service2"}

        "name is updated on revision -- found with all-revisions true"
        [svc2-1]
        {:name "Service2" :all-revisions true}

        "all-revisions true"
        [svc1-1 svc1-2-tombstone svc1-3 svc2-1 svc2-2 svc2-3-tombstone svc3]
        {:all-revisions true}))))

(deftest search-all-revisions-error-cases
  (testing "service search with all_revisions bad value"
    (let [{:keys [status errors]} (search/find-refs :service {:all-revisions "foo"})]
      (is (= [400 ["Parameter all_revisions must take value of true, false, or unset, but was [foo]"]]
             [status errors])))))
