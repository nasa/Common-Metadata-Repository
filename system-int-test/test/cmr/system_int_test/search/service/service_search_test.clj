(ns cmr.system-int-test.search.service.service-search-test
  "This tests searching services."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as services]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                services/grant-all-service-fixture]))

(deftest search-for-services-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400, :errors ["Parameter [foo] was not recognized."]}
           (search/find-refs :service {:foo "bar"}))))

  (testing "Unsupported sort-key parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting services."]}
           (search/find-refs :service {:sort-key "concept_id"}))))

  (testing "Default service search result format is XML"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :service {})]
      (is (= 200 status))
      (is (= "application/xml; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format in headers"
    (is (= {:errors ["The mime type [application/atom+xml] is not supported for services."]
            :status 400}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :atom+xml :service {})))))

  (testing "Unsuported result format in url extension"
    (is (= {:errors ["The mime type [application/atom+xml] is not supported for services."]
            :status 400}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :service {} {:url-extension "atom"}))))))

(deftest search-for-services-test
  (let [service1 (services/ingest-service-with-attrs {:native-id "svc1"
                                                      :Name "Service1"
                                                      :provider-id "PROV1"})
        service2 (services/ingest-service-with-attrs {:native-id "svc2"
                                                      :Name "Service2"
                                                      :provider-id "PROV1"})
        service3 (services/ingest-service-with-attrs {:native-id "svc3"
                                                      :Name "a sub for service2"
                                                      :provider-id "PROV2"})
        service4 (services/ingest-service-with-attrs {:native-id "svc4"
                                                      :Name "v.other"
                                                      :provider-id "PROV2"})
        prov1-services [service1 service2]
        prov2-services [service3 service4]
        all-services (concat prov1-services prov2-services)]
    (index/wait-until-indexed)

    (are3 [expected-services query]
      (d/refs-match? expected-services (search/find-refs :service query))

      "Find all"
      all-services {}

      ;; more search tests on various permitted params go here
      )))

(deftest deleted-services-not-found-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection 1 {})
                                            {:token token})
        svc1-concept (services/make-service-concept {:native-id "svc1"
                                                     :Name "Service1"})
        service1 (services/ingest-service svc1-concept {:token token})
        svc2-concept (services/make-service-concept {:native-id "svc2"
                                                     :Name "Service2"})
        service2 (services/ingest-service svc2-concept {:token token})
        all-services [service1 service2]]
    (index/wait-until-indexed)

    ;; Now I should find the all services when searching
    (d/refs-match? all-services (search/find-refs :service {}))

    ;; Delete service1
    (ingest/delete-concept svc1-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching services does not find the deleted service
    (d/refs-match? [service2] (search/find-refs :service {}))

    ;; Now verify that after we delete a service that has service association,
    ;; we can't find it through search
    ;; create service associations on service2
    (au/associate-by-concept-ids token
                                 (:concept-id service2)
                                 [{:concept-id (:concept-id coll1)}])
    ;; Delete service2
    (ingest/delete-concept svc2-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching services does not find the deleted services
    (d/refs-match? [] (search/find-refs :service {}))))

(deftest service-search-sort
  (let [service1 (services/ingest-service-with-attrs {:native-id "svc1"
                                                      :Name "service"
                                                      :provider-id "PROV2"})
        service2 (services/ingest-service-with-attrs {:native-id "svc2"
                                                      :Name "Service 2"
                                                      :provider-id "PROV1"})
        service3 (services/ingest-service-with-attrs {:native-id "svc3"
                                                      :Name "a service"
                                                      :provider-id "PROV1"})
        service4 (services/ingest-service-with-attrs {:native-id "svc4"
                                                      :Name "service"
                                                      :provider-id "PROV1"})]
    (index/wait-until-indexed)

    (are3 [sort-key expected-services]
      (is (d/refs-match-order?
           expected-services
           (search/find-refs :service {:sort-key sort-key})))

      "Default sort"
      nil
      [service3 service4 service1 service2]

      "Sort by name"
      "name"
      [service3 service4 service1 service2]

      "Sort by name descending order"
      "-name"
      [service2 service4 service1 service3]

      "Sort by provider id"
      "provider_id"
      [service2 service3 service4 service1]

      "Sort by provider id descending order"
      "-provider_id"
      [service1 service2 service3 service4]

      "Sort by revision-date"
      "revision_date"
      [service1 service2 service3 service4]

      "Sort by revision-date descending order"
      "-revision_date"
      [service4 service3 service2 service1]

      "Sort by name ascending then provider id ascending explicitly"
      ["name" "provider_id"]
      [service3 service4 service1 service2]

      "Sort by name ascending then provider id descending order"
      ["name" "-provider_id"]
      [service3 service1 service4 service2]

      "Sort by name then provider id descending order"
      ["-name" "-provider_id"]
      [service2 service1 service4 service3])))
