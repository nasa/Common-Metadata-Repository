(ns cmr.system-int-test.search.service.collection-service-search-test
  "Tests searching for collections with associated services"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.atom :as atom]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service-util]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                service-util/grant-all-service-fixture]))

(defn- verify-collection-atom-has-formats
  "Verify the collection ATOM response has-formats field has the given value"
  [coll has-formats]
  (let [coll-with-extra-fields (assoc coll :has-formats has-formats)
        {:keys [entry-title]} coll
        coll-atom (atom/collections->expected-atom
                   [coll-with-extra-fields]
                   (format "collections.atom?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-atom
                                  :collection {:entry-title entry-title})]

    (is (= [200 coll-atom]
           [status results]))))

(defn- verify-collection-json-has-formats
  "Verify the collection JSON response has-formats field has the given value"
  [coll has-formats]
  (let [coll-with-extra-fields (assoc coll :has-formats has-formats)
        {:keys [entry-title]} coll
        coll-json (atom/collections->expected-atom
                   [coll-with-extra-fields]
                   (format "collections.json?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-json
                                  :collection {:entry-title entry-title})]

    (is (= [200 coll-json]
           [status results]))))

(defn- verify-collection-has-formats
  "Verify the collection in ATOM and JSON response has-formats field has the given value"
  [coll has-formats]
  (verify-collection-atom-has-formats coll has-formats)
  (verify-collection-json-has-formats coll has-formats))

(deftest collection-service-search-atom-json-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "ET1"
                                                :short-name "S1"
                                                :version-id "V1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "ET2"
                                                :short-name "S2"
                                                :version-id "V2"}))
        ;; create services
        {serv1-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv1"
                                         :Name "service1"})
        {serv2-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv2"
                                         :Name "service2"
                                         :ServiceOptions {:SupportedFormats ["image/png"]}})
        {serv3-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv3"
                                         :Name "service3"
                                         :ServiceOptions {:SupportedFormats ["image/tiff"]}})
        serv4-concept (service-util/make-service-concept
                       {:native-id "serv4"
                        :Name "Service4"
                        :ServiceOptions {:SupportedFormats ["image/png" "image/tiff"]}})
        {serv4-concept-id :concept-id} (service-util/ingest-service serv4-concept)]
    ;; index the collections so that they can be found during service association
    (index/wait-until-indexed)
    (au/associate-by-concept-ids token serv1-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with a service with no supported format, has-formats false
    (verify-collection-has-formats coll1 false)
    (verify-collection-has-formats coll2 false)

    (au/associate-by-concept-ids token serv2-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (au/associate-by-concept-ids token serv3-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with services with just one supported format, has-formats false
    (verify-collection-has-formats coll1 false)
    (verify-collection-has-formats coll2 false)

    (au/associate-by-concept-ids token serv4-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with services with two supported formats, has-formats true
    (verify-collection-has-formats coll1 true)
    (verify-collection-has-formats coll2 true)

    (testing "delete service affect collection search has-formats field"
      ;; Delete service4
      (ingest/delete-concept serv4-concept {:token token})
      (index/wait-until-indexed)

      ;; verify has-formats is false after the service with two supported formats is deleted
      (verify-collection-has-formats coll1 false)
      (verify-collection-has-formats coll2 false))

    (testing "update service affect collection search has-formats field"
      ;; before update service3, collections' has formats is false
      (verify-collection-has-formats coll1 false)
      (verify-collection-has-formats coll2 false)
      ;; update service3 to have two supported formats
      (service-util/ingest-service-with-attrs
       {:native-id "serv3"
        :Name "service3"
        :ServiceOptions {:SupportedFormats ["image/tiff" "JPEG"]}})
      (index/wait-until-indexed)

      ;; verify has-formats is true after the service is updated with two supported formats
      (verify-collection-has-formats coll1 true)
      (verify-collection-has-formats coll2 true))))
