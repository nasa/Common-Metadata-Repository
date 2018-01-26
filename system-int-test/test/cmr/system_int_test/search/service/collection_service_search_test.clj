(ns cmr.system-int-test.search.service.collection-service-search-test
  "Tests searching for collections with associated services"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.atom :as atom]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service-util]
   [cmr.system-int-test.utils.variable-util :as variable-util]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                service-util/grant-all-service-fixture]))

(defn- assert-collection-atom-result
  "Verify the collection ATOM response has-formats, has-variables, has transforms fields
  have the correct values"
  [coll expected-fields]
  (let [coll-with-extra-fields (merge coll expected-fields)
        {:keys [entry-title]} coll
        coll-atom (atom/collections->expected-atom
                   [coll-with-extra-fields]
                   (format "collections.atom?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-atom
                                  :collection {:entry-title entry-title})]

    (is (= [200 coll-atom]
           [status results]))))

(defn- assert-collection-json-result
  "Verify the collection JSON response associations related fields have the correct values"
  [coll expected-fields serv-concept-ids var-concept-ids]
  (let [coll-with-extra-fields (merge coll
                                      expected-fields
                                      {:services serv-concept-ids
                                       :variables var-concept-ids})
        {:keys [entry-title]} coll
        coll-json (atom/collections->expected-atom
                   [coll-with-extra-fields]
                   (format "collections.json?entry_title=%s" entry-title))
        {:keys [status results]} (search/find-concepts-json
                                  :collection {:entry-title entry-title})]

    (is (= [200 coll-json]
           [status results]))))

(defn- assert-collection-atom-json-result
  "Verify collection in ATOM and JSON response has-formats, has-variables, has-transforms,
  has-spatial-subsetting and associations fields"
  [coll expected-fields serv-concept-ids var-concept-ids]
  (let [expected-fields (merge {:has-formats false
                                :has-variables false
                                :has-transforms false
                                :has-spatial-subsetting false}
                               {:has-variables (some? (seq var-concept-ids))}
                               expected-fields)]
    (assert-collection-atom-result coll expected-fields)
    (assert-collection-json-result coll expected-fields serv-concept-ids var-concept-ids)))

(defn- assert-collection-umm-json-result
  "Verify collection in UMM JSON response has-formats, has-variables, has-transforms,
  has-spatial-subsetting and associations fields"
  [coll expected-fields serv-concept-ids var-concept-ids]
  (let [expected-fields (merge {:has-formats false
                                :has-variables false
                                :has-transforms false
                                :has-spatial-subsetting false}
                               {:has-variables (some? (seq var-concept-ids))}
                               expected-fields)
        coll-with-extra-fields (merge coll
                                      expected-fields
                                      {:services serv-concept-ids
                                       :variables var-concept-ids})
        options {:accept (mt/with-version mt/umm-json-results umm-version/current-collection-version)}
        {:keys [entry-title]} coll
        response (search/find-concepts-umm-json :collection {:entry-title entry-title} options)]
    (du/assert-umm-jsons-match
     umm-version/current-collection-version [coll-with-extra-fields] response)))

(defn- assert-collection-search-result
  "Verify collection in ATOM, JSON and UMM JSON response has-formats, has-variables,
  has-transforms, has-spatial-subsetting and associations fields"
  ([coll expected-fields serv-concept-ids]
   (assert-collection-search-result coll expected-fields serv-concept-ids nil))
  ([coll expected-fields serv-concept-ids var-concept-ids]
   (assert-collection-atom-json-result coll expected-fields serv-concept-ids var-concept-ids)
   (assert-collection-umm-json-result coll expected-fields serv-concept-ids var-concept-ids)))

(deftest collection-service-search-result-fields-test
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
    (assert-collection-search-result coll1 {:has-formats false} [serv1-concept-id])
    (assert-collection-search-result coll2 {:has-formats false} [serv1-concept-id])

    (au/associate-by-concept-ids token serv2-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (au/associate-by-concept-ids token serv3-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with services with just one supported format, has-formats false
    (assert-collection-search-result
     coll1 {:has-formats false} [serv1-concept-id serv2-concept-id serv3-concept-id])
    (assert-collection-search-result
     coll2 {:has-formats false} [serv1-concept-id serv2-concept-id serv3-concept-id])

    (au/associate-by-concept-ids token serv4-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with services with two supported formats, has-formats true
    (assert-collection-search-result
     coll1 {:has-formats true} [serv1-concept-id serv2-concept-id serv3-concept-id serv4-concept-id])
    (assert-collection-search-result
     coll2 {:has-formats true} [serv1-concept-id serv2-concept-id serv3-concept-id serv4-concept-id])

    (testing "delete service affect collection search has-formats field"
      ;; Delete service4
      (ingest/delete-concept serv4-concept {:token token})
      (index/wait-until-indexed)

      ;; verify has-formats is false after the service with two supported formats is deleted
      (assert-collection-search-result
       coll1 {:has-formats false} [serv1-concept-id serv2-concept-id serv3-concept-id])
      (assert-collection-search-result
       coll2 {:has-formats false} [serv1-concept-id serv2-concept-id serv3-concept-id]))

    (testing "update service affect collection search has-formats field"
      ;; before update service3, collections' has formats is false
      (assert-collection-search-result
       coll1 {:has-formats false} [serv1-concept-id serv2-concept-id serv3-concept-id])
      (assert-collection-search-result
       coll2 {:has-formats false} [serv1-concept-id serv2-concept-id serv3-concept-id])
      ;; update service3 to have two supported formats and spatial subsetting
      (service-util/ingest-service-with-attrs
       {:native-id "serv3"
        :Name "service3"
        :ServiceOptions {:SupportedFormats ["image/tiff" "JPEG"]
                         :SubsetTypes ["Spatial"]}})
      (index/wait-until-indexed)

      ;; verify has-formats is true after the service is updated with two supported formats
      (assert-collection-search-result
       coll1
       {:has-formats true :has-transforms true :has-spatial-subsetting true}
       [serv1-concept-id serv2-concept-id serv3-concept-id])
      (assert-collection-search-result
       coll2
       {:has-formats true :has-transforms true :has-spatial-subsetting true}
       [serv1-concept-id serv2-concept-id serv3-concept-id]))

    (testing "variable associations together with service associations"
      (let [{var-concept-id :concept-id} (variable-util/ingest-variable-with-attrs
                                          {:native-id "var1"
                                           :Name "Variable1"})]
        (au/associate-by-concept-ids token var-concept-id [{:concept-id (:concept-id coll1)}])
        (index/wait-until-indexed)
        (assert-collection-search-result
         coll1
         {:has-formats true :has-transforms true :has-spatial-subsetting true}
         [serv1-concept-id serv2-concept-id serv3-concept-id] [var-concept-id])
        (assert-collection-search-result
         coll2
         {:has-formats true :has-transforms true :has-spatial-subsetting true}
         [serv1-concept-id serv2-concept-id serv3-concept-id])))))

(deftest collection-service-search-has-transforms-and-service-deletion-test
  (testing "SupportedProjections affects has-transforms"
    (let [token (e/login (s/context) "user1")
          coll1 (d/ingest "PROV1" (dc/collection {:entry-title "ET1"
                                                  :short-name "S1"
                                                  :version-id "V1"}))
          coll2 (d/ingest "PROV1" (dc/collection {:entry-title "ET2"
                                                  :short-name "S2"
                                                  :version-id "V2"}))
          ;; create services
          {serv1-concept-id :concept-id}
          (service-util/ingest-service-with-attrs
           {:native-id "serv1"
            :Name "service1"})

          {serv2-concept-id :concept-id}
          (service-util/ingest-service-with-attrs
           {:native-id "serv2"
            :Name "service2"
            :ServiceOptions {:SupportedProjections ["WGS 84 / Antarctic Polar Stereographic"]}})

          {serv3-concept-id :concept-id}
          (service-util/ingest-service-with-attrs
           {:native-id "serv3"
            :Name "service3"
            :ServiceOptions {:SupportedProjections ["WGS84 - World Geodetic System 1984"]}})

          {serv4-concept-id :concept-id}
          (service-util/ingest-service-with-attrs
           {:native-id "serv4"
            :Name "Service4"
            :ServiceOptions {:SupportedProjections ["WGS 84 / Antarctic Polar Stereographic"
                                                    "WGS84 - World Geodetic System 1984"]}})]
      ;; index the collections so that they can be found during service association
      (index/wait-until-indexed)
      (au/associate-by-concept-ids token serv1-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (index/wait-until-indexed)

      ;; verify collection associated with a service with no supported projection, has-transforms false
      (assert-collection-search-result coll1 {:has-transforms false} [serv1-concept-id])
      (assert-collection-search-result coll2 {:has-transforms false} [serv1-concept-id])

      (au/associate-by-concept-ids token serv2-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (au/associate-by-concept-ids token serv3-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (index/wait-until-indexed)

      ;; verify collection associated with services with just one supported projection, has-transforms false
      (assert-collection-search-result
       coll1 {:has-transforms false} [serv1-concept-id serv2-concept-id serv3-concept-id])
      (assert-collection-search-result
       coll2 {:has-transforms false} [serv1-concept-id serv2-concept-id serv3-concept-id])

      (au/associate-by-concept-ids token serv4-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (index/wait-until-indexed)

      ;; verify collection associated with services with two supported projections, has-transforms true
      (assert-collection-search-result
       coll1 {:has-transforms true}
       [serv1-concept-id serv2-concept-id serv3-concept-id serv4-concept-id])
      (assert-collection-search-result
       coll2 {:has-transforms true}
       [serv1-concept-id serv2-concept-id serv3-concept-id serv4-concept-id])))

  (let [token (e/login (s/context) "user1")
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "ET3"
                                                :short-name "S3"
                                                :version-id "V3"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "ET4"
                                                :short-name "S4"
                                                :version-id "V4"}))
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "ET5"
                                                :short-name "S5"
                                                :version-id "V5"}))
        ;; create services
        {serv8-concept-id :concept-id}
        (service-util/ingest-service-with-attrs
         {:native-id "serv8"
          :Name "service8"
          :ServiceOptions {:SubsetTypes ["Spatial"]}})

        {serv9-concept-id :concept-id}
        (service-util/ingest-service-with-attrs
         {:native-id "serv9"
          :Name "service9"
          :ServiceOptions {:InterpolationTypes ["Bilinear Interpolation"]}})

        ;; service for testing non-spatial SubsetType and service deletion
        serv10-concept (service-util/make-service-concept
                        {:native-id "serv10"
                         :Name "service10"
                         :ServiceOptions {:SubsetTypes ["Variable"]}})
        {serv10-concept-id :concept-id} (service-util/ingest-service serv10-concept)

        ;; service for testing service deletion affects collection search result
        serv11-concept (service-util/make-service-concept
                        {:native-id "serv11"
                         :Name "service11"
                         :ServiceOptions {:SubsetTypes ["Spatial"]
                                          :SupportedFormats ["image/tiff" "JPEG"]}})
        {serv11-concept-id :concept-id} (service-util/ingest-service serv11-concept)]
    (index/wait-until-indexed)

    (testing "SubsetType affects has-transforms"
      ;; sanity check before the association is made
      (assert-collection-search-result
       coll3 {:has-transforms false :has-spatial-subsetting false} [])

      ;; associate coll3 with a service that has SubsetType
      (au/associate-by-concept-ids token serv8-concept-id [{:concept-id (:concept-id coll3)}])
      (index/wait-until-indexed)

      ;; after service association is made, has-transforms is true
      (assert-collection-search-result
       coll3 {:has-transforms true :has-spatial-subsetting true} [serv8-concept-id]))

    (testing "InterpolationTypes affects has-transforms"
      ;; sanity check before the association is made
      (assert-collection-search-result coll4 {:has-transforms false} [])

      ;; associate coll4 with a service that has InterpolationTypes
      (au/associate-by-concept-ids token serv9-concept-id [{:concept-id (:concept-id coll4)}])
      (index/wait-until-indexed)

      ;; after service association is made, has-transforms is true
      (assert-collection-search-result coll4 {:has-transforms true} [serv9-concept-id]))

    (testing "Non-spatial SubsetType does not affect has-spatial-subsetting"
      ;; sanity check before the association is made
      (assert-collection-search-result
       coll5 {:has-spatial-subsetting false} [])

      ;; associate coll5 with a service that has SubsetTypes
      (au/associate-by-concept-ids token serv10-concept-id [{:concept-id (:concept-id coll5)}])
      (index/wait-until-indexed)

      ;; after service association is made, has-transforms is true
      (assert-collection-search-result
       coll5 {:has-spatial-subsetting false :has-transforms true} [serv10-concept-id])

      (testing "deletion of service affects collection search service association fields"
        ;; associate coll5 also with service11 to make other service related fields true
        (au/associate-by-concept-ids token serv11-concept-id [{:concept-id (:concept-id coll5)}])
        (index/wait-until-indexed)

        ;; sanity check that all service related fields are true and service associations are present
        (assert-collection-search-result
         coll5
         {:has-formats true :has-transforms true :has-spatial-subsetting true}
         [serv10-concept-id serv11-concept-id])

        ;; Delete service11
        (ingest/delete-concept serv11-concept {:token token})
        (index/wait-until-indexed)

        ;; verify the service related fields affected by service11 are set properly after deletion
        (assert-collection-search-result
         coll5
         {:has-formats false :has-transforms true :has-spatial-subsetting false}
         [serv10-concept-id])

        ;; Delete service10
        (ingest/delete-concept serv10-concept {:token token})
        (index/wait-until-indexed)

        ;; verify service related has_* fields are false and associations is empty now
        (assert-collection-search-result
         coll5
         {:has-formats false :has-transforms false :has-spatial-subsetting false}
         [])))))

(deftest collection-service-search-test
  (let [token (e/login (s/context) "user1")
        [coll1 coll2 coll3 coll4 coll5] (doall (for [n (range 1 6)]
                                                 (d/ingest-umm-spec-collection
                                                  "PROV1"
                                                  (data-umm-c/collection n {})
                                                  {:token token})))
        ;; index the collections so that they can be found during service association
        _ (index/wait-until-indexed)
        ;; create services
        {serv1-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv1"
                                         :Name "Service1"})
        {serv2-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv2"
                                         :Name "Service2"})
        {serv3-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "someServ"
                                         :Name "SomeService"})
        {serv4-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "S4"
                                         :Name "Name4"})]

    ;; create service associations
    ;; service1 is associated with coll1 and coll2
    (au/associate-by-concept-ids token serv1-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    ;; service2 is associated with coll2 and coll3
    (au/associate-by-concept-ids token serv2-concept-id [{:concept-id (:concept-id coll2)}
                                                         {:concept-id (:concept-id coll3)}])
    ;; SomeService is associated with coll4
    (au/associate-by-concept-ids token serv3-concept-id [{:concept-id (:concept-id coll4)}])
    (index/wait-until-indexed)

    (testing "search collections by service names"
      (are3 [items service options]
        (let [params (merge {:service_name service}
                            (when options
                              {"options[service_name]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single service search"
        [coll1 coll2] "service1" {}

        "no matching service"
        [] "service3" {}

        "multiple services"
        [coll1 coll2 coll3] ["service1" "service2"] {}

        "AND option false"
        [coll1 coll2 coll3] ["service1" "service2"] {:and false}

        "AND option true"
        [coll2] ["service1" "service2"] {:and true}

        "pattern true"
        [coll1 coll2 coll3] "Serv*" {:pattern true}

        "pattern false"
        [] "Serv*" {:pattern false}

        "default pattern is false"
        [] "Serv*" {}

        "ignore-case true"
        [coll1 coll2] "service1" {:ignore-case true}

        "ignore-case false"
        [] "service1" {:ignore-case false}))

    (testing "search collections by service concept-ids"
      (are3 [items service options]
        (let [params (merge {:service_concept_id service}
                            (when options
                              {"options[service_concept_id]" options}))]
          (d/refs-match? items (search/find-refs :collection params)))

        "single service search"
        [coll1 coll2] serv1-concept-id {}

        "service concept id search is case sensitive"
        [] (string/lower-case serv1-concept-id) {}

        "no matching service"
        [] serv4-concept-id {}

        "multiple services"
        [coll1 coll2 coll3] [serv1-concept-id serv2-concept-id] {}

        "AND option false"
        [coll1 coll2 coll3] [serv1-concept-id serv2-concept-id] {:and false}

        "AND option true"
        [coll2] [serv1-concept-id serv2-concept-id] {:and true}))))
