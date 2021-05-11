(ns cmr.system-int-test.search.service.collection-service-search-test
  "Tests searching for collections with associated services"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service-util]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1"})
                service-util/grant-all-service-fixture]))

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
        {serv3-concept-id :concept-id} (service-util/ingest-service-with-attrs
                                        {:native-id "serv3"
                                         :Name "service3"
                                         :ServiceOptions {:SupportedReformattings [{:SupportedInputFormat "TIFF"
                                                                                    :SupportedOutputFormats ["TIFF"]}]}})
        serv4-concept (service-util/make-service-concept
                       {:native-id "serv4"
                        :Name "Service4"
                        :ServiceOptions {:SupportedReformattings [{:SupportedInputFormat "TIFF"
                                                                   :SupportedOutputFormats ["PNG"]}]}})
        {serv4-concept-id :concept-id} (service-util/ingest-service serv4-concept)]
    ;; index the collections so that they can be found during service association
    (index/wait-until-indexed)
    (au/associate-by-concept-ids token serv1-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with a service with no supported output format, has-formats false
    (service-util/assert-collection-search-result
     coll1 {:has-formats false} [serv1-concept-id])
    (service-util/assert-collection-search-result
     coll2 {:has-formats false} [serv1-concept-id])

    (au/associate-by-concept-ids token serv3-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with services with a single output format that matches
    ;; the its single input format, has-formats false
    (service-util/assert-collection-search-result
     coll1 {:has-formats false} [serv1-concept-id serv3-concept-id])
    (service-util/assert-collection-search-result
     coll2 {:has-formats false} [serv1-concept-id serv3-concept-id])

    (au/associate-by-concept-ids token serv4-concept-id [{:concept-id (:concept-id coll1)}
                                                         {:concept-id (:concept-id coll2)}])
    (index/wait-until-indexed)

    ;; verify collection associated with services with one supported output formats that does not
    ;; match its input formats, has-formats true
    (service-util/assert-collection-search-result
     coll1
     {:has-formats true :service-features {:opendap {:has-formats true}}}
     [serv1-concept-id serv3-concept-id serv4-concept-id])
    (service-util/assert-collection-search-result
     coll2
     {:has-formats true :service-features {:opendap {:has-formats true}}}
     [serv1-concept-id serv3-concept-id serv4-concept-id])

    (testing "delete service affect collection search has-formats field"
      ;; Delete service4
      (ingest/delete-concept serv4-concept {:token token})
      (index/wait-until-indexed)

      ;; verify has-formats is false after the service with two supported formats is deleted
      (service-util/assert-collection-search-result
       coll1 {:has-formats false} [serv1-concept-id serv3-concept-id])
      (service-util/assert-collection-search-result
       coll2 {:has-formats false} [serv1-concept-id serv3-concept-id]))

    (testing "update service affect collection search has-formats field"
      ;; before update service3, collections' has formats is false
      (service-util/assert-collection-search-result
       coll1
       {:has-formats false :has-transforms false :has-spatial-subsetting false
        :has-temporal-subsetting false :has-variables false}
       [serv1-concept-id serv3-concept-id])
      (service-util/assert-collection-search-result
       coll2
       {:has-formats false :has-transforms false :has-spatial-subsetting false
        :has-temporal-subsetting false :has-variables false}
       [serv1-concept-id serv3-concept-id])

      ;; update service3 to have two supported formats and spatial subsetting
      (service-util/ingest-service-with-attrs
       {:native-id "serv3"
        :Name "service3"
        :ServiceOptions {:SupportedReformattings [{:SupportedInputFormat "PNG"
                                                   :SupportedOutputFormats ["TIFF" "JPEG"]}]
                         :Subset {:SpatialSubset {:BoundingBox {:AllowMultipleValues false}}}}})

      (index/wait-until-indexed)
      ;; verify has-formats is true after the service is updated with two supported formats
      ;; and has-spatial-subsetting is true after service is updated with Spatial SubsetTypes
      ;; the has-transforms is not affected by the SupportedInputFormats or SubsetTypes
      (service-util/assert-collection-search-result
       coll1
       {:has-formats true :has-transforms false :has-spatial-subsetting true
        :has-temporal-subsetting false :has-variables false
        :service-features {:opendap {:has-formats true :has-spatial-subsetting true}}}
       [serv1-concept-id serv3-concept-id])

      ;; update service3 to temporal subsetting and  also have InterpolationTypes
      (service-util/ingest-service-with-attrs
       {:native-id "serv3"
        :Name "service3"
        :ServiceOptions {:SupportedReformattings [{:SupportedInputFormat "PNG"
                                                   :SupportedOutputFormats ["TIFF" "JPEG"]}]
                         :Subset {:TemporalSubset {:AllowMultipleValues false}}
                         :InterpolationTypes ["Nearest Neighbor"]}})
      (index/wait-until-indexed)
      ;; verify has-transforms is true after the service is updated with InterpolationTypes
      (service-util/assert-collection-search-result
       coll1
       {:has-formats true :has-transforms true :has-spatial-subsetting false
        :has-temporal-subsetting true :has-variables false
        :service-features {:opendap
                           {:has-formats true :has-transforms true :has-temporal-subsetting true}}}
       [serv1-concept-id serv3-concept-id])
      (service-util/assert-collection-search-result
       coll2
       {:has-formats true :has-transforms true :has-spatial-subsetting false
        :has-temporal-subsetting true :has-variables false
        :service-features {:opendap
                           {:has-formats true :has-transforms true :has-temporal-subsetting true}}}
       [serv1-concept-id serv3-concept-id]))

    (testing "variable associations together with service associations"
      (let [var-concept (variable-util/make-variable-concept
                         {:Name "Variable1"}
                         {:native-id "var1"
                          :coll-concept-id (:concept-id coll1)})
            {var-concept-id :concept-id} (variable-util/ingest-variable-with-association var-concept)]
        (index/wait-until-indexed)
        ;; verify coll1 has-variables is true after associated with a variable,
        ;; coll2 has-variables is still false
        (service-util/assert-collection-search-result
         coll1
         {:has-formats true :has-transforms true :has-spatial-subsetting false
          :has-temporal-subsetting true :has-variables true
          :service-features {:opendap
                             {:has-formats true
                              :has-transforms true
                              :has-temporal-subsetting true
                              ;; service level has-variables is not affected by variable association
                              :has-variables false}}}
         [serv1-concept-id serv3-concept-id] [var-concept-id])
        (service-util/assert-collection-search-result
         coll2
         {:has-formats true :has-transforms true :has-spatial-subsetting false
          :has-temporal-subsetting true :has-variables false
          :service-features {:opendap
                             {:has-formats true
                              :has-transforms true
                              :has-temporal-subsetting true}}}
         [serv1-concept-id serv3-concept-id])))))

(deftest collection-service-search-has-transforms-and-service-deletion-test
  (testing "SupportedInputProjections and SupportedOutputProjections affects has-transforms"
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
            :ServiceOptions {:SupportedInputProjections [{:ProjectionName "Mercator"}]}})

          {serv3-concept-id :concept-id}
          (service-util/ingest-service-with-attrs
           {:native-id "serv3"
            :Name "service3"
            :ServiceOptions {:SupportedInputProjections [{:ProjectionName "Sinusoidal"}]}})

          ;; The service also test service features for Type not OPeNDAP, ESI or Harmony
          {serv4-concept-id :concept-id}
          (service-util/ingest-service-with-attrs
           {:native-id "serv4"
            :Name "Service4"
            :Type "THREDDS"
            :ServiceOptions {:SupportedOutputProjections [{:ProjectionName "Mercator"}
                                                          {:ProjectionName "Sinusoidal"}]}})]
      ;; index the collections so that they can be found during service association
      (index/wait-until-indexed)
      (au/associate-by-concept-ids token serv1-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (index/wait-until-indexed)

      ;; verify collection associated with a service with no supported projection, has-transforms false
      (service-util/assert-collection-search-result coll1 {:has-transforms false} [serv1-concept-id])
      (service-util/assert-collection-search-result coll2 {:has-transforms false} [serv1-concept-id])

      (au/associate-by-concept-ids token serv2-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (au/associate-by-concept-ids token serv3-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (index/wait-until-indexed)

      ;; verify collection associated with services with just one supported projection, has-transforms false
      (service-util/assert-collection-search-result
       coll1 {:has-transforms false} [serv1-concept-id serv2-concept-id serv3-concept-id])
      (service-util/assert-collection-search-result
       coll2 {:has-transforms false} [serv1-concept-id serv2-concept-id serv3-concept-id])

      (au/associate-by-concept-ids token serv4-concept-id [{:concept-id (:concept-id coll1)}
                                                           {:concept-id (:concept-id coll2)}])
      (index/wait-until-indexed)

      ;; verify collection associated with services with two supported projections, has-transforms true
      ;; And since Service 4 is not of Type OPeNDAP, ESI or Harmony, there is no service features
      (service-util/assert-collection-search-result
       coll1
       {:has-transforms true}
       [serv1-concept-id serv2-concept-id serv3-concept-id serv4-concept-id])
      (service-util/assert-collection-search-result
       coll2
       {:has-transforms true}
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
          :Type "OPeNDAP"
          :ServiceOptions {:Subset {:SpatialSubset {:BoundingBox {:AllowMultipleValues false}}}}})

        {serv9-concept-id :concept-id}
        (service-util/ingest-service-with-attrs
         {:native-id "serv9"
          :Name "service9"
          :Type "ESI"
          :ServiceOptions {:InterpolationTypes ["Bilinear Interpolation"]}})

        ;; service for testing non-spatial SubsetType and service deletion
        serv10-concept (service-util/make-service-concept
                        {:native-id "serv10"
                         :Name "service10"
                         :Type "Harmony"
                         :ServiceOptions {:Subset {:VariableSubset {:AllowMultipleValues false}}}})
        {serv10-concept-id :concept-id} (service-util/ingest-service serv10-concept)

        ;; service for testing service deletion affects collection search result
        serv11-concept (service-util/make-service-concept
                        {:native-id "serv11"
                         :Name "service11"
                         :ServiceOptions {:Subset {:SpatialSubset {:BoundingBox {:AllowMultipleValues false}}}
                                          :SupportedReformattings [{:SupportedInputFormat "HDF4"
                                                                    :SupportedOutputFormats ["TIFF" "JPEG"]}]
                                          :InterpolationTypes ["Nearest Neighbor"]}})
        {serv11-concept-id :concept-id} (service-util/ingest-service serv11-concept)]
    (index/wait-until-indexed)

    (testing "SubsetType does not affect has-transforms, also testing OPeNDAP service features"
      ;; sanity check before the association is made
      (service-util/assert-collection-search-result
       coll3 {:has-transforms false :has-spatial-subsetting false} [])

      ;; associate coll3 with a service that has SubsetType
      (au/associate-by-concept-ids token serv8-concept-id [{:concept-id (:concept-id coll3)}])
      (index/wait-until-indexed)

      ;; after service association is made, has-transforms is still false
      (service-util/assert-collection-search-result
       coll3
       {:has-transforms false
        :has-spatial-subsetting true
        :service-features {:opendap {:has-spatial-subsetting true}}}
       [serv8-concept-id]))

    (testing "InterpolationTypes affects has-transforms, also testing ESI service features"
      ;; sanity check before the association is made
      (service-util/assert-collection-search-result coll4 {:has-transforms false} [])

      ;; associate coll4 with a service that has InterpolationTypes
      (au/associate-by-concept-ids token serv9-concept-id [{:concept-id (:concept-id coll4)}])
      (index/wait-until-indexed)

      ;; after service association is made, has-transforms is true
      (service-util/assert-collection-search-result
       coll4
       {:has-transforms true
        :service-features {:esi {:has-transforms true}}}
       [serv9-concept-id]))

    (testing "Non-spatial SubsetType does not affect has-spatial-subsetting, also testing Harmony service features"
      ;; sanity check before the association is made
      (service-util/assert-collection-search-result
       coll5 {:has-variables false :has-spatial-subsetting false :has-transforms false} [])

      ;; associate coll5 with a service that has SubsetTypes of Variable
      (au/associate-by-concept-ids token serv10-concept-id [{:concept-id (:concept-id coll5)}])
      (index/wait-until-indexed)

      ;; after service association is made, has variables is true and has-transforms is still false
      (service-util/assert-collection-search-result
       coll5
       {:has-variables true
        :has-spatial-subsetting false
        :has-transforms false
        :service-features {:harmony {:has-variables true}}}
       [serv10-concept-id])

      (testing "deletion of service affects collection search service association fields, also testing mixed service features"
        ;; associate coll5 also with service11 to make other service related fields true
        (au/associate-by-concept-ids token serv11-concept-id [{:concept-id (:concept-id coll5)}])
        (index/wait-until-indexed)

        ;; sanity check that all service related fields are true and service associations are present
        (service-util/assert-collection-search-result
         coll5
         {:has-variables true
          :has-formats true
          :has-transforms true
          :has-spatial-subsetting true
          :service-features {:harmony {:has-variables true}
                             :opendap {:has-variables false
                                       :has-formats true
                                       :has-transforms true
                                       :has-spatial-subsetting true}}}
         [serv10-concept-id serv11-concept-id])

        ;; Delete service11
        (ingest/delete-concept serv11-concept {:token token})
        (index/wait-until-indexed)

        ;; verify the service related fields affected by service11 are set properly after deletion
        (service-util/assert-collection-search-result
         coll5
         {:has-variables true
          :has-formats false
          :has-transforms false
          :has-spatial-subsetting false
          :service-features {:harmony {:has-variables true}}}
         [serv10-concept-id])

        ;; Delete service10
        (ingest/delete-concept serv10-concept {:token token})
        (index/wait-until-indexed)

        ;; verify service related has_* fields are false and associations is empty now
        (service-util/assert-collection-search-result
         coll5
         {:has-variables false :has-formats false :has-transforms false :has-spatial-subsetting false}
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
                                         :Name "SomeService"
                                         :Type "Harmony"})
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
        [coll2] [serv1-concept-id serv2-concept-id] {:and true}))

    (testing "search collections by service types"
      (are3 [items service]
        (let [params (merge {:service_type service})]
          (d/refs-match? items (search/find-refs :collection params)))

        "single service search"
        [coll4] "Harmony"

        "different single service search"
        [coll1 coll2 coll3] "OPeNDAP"

        "multiple search search"
        [coll1 coll2 coll3 coll4] ["Harmony" "OPeNDAP"]))))
