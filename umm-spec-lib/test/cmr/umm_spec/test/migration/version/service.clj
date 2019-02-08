(ns cmr.umm-spec.test.migration.version.service
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.mime-types :as mt]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as v]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

(def service-concept-1-0
  {:RelatedURL {:URLContentType "CollectionURL"
                :Description "OPeNDAP Service"
                :Type "GET SERVICE"
                :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"},
   :Coverage {:Type "SPATIAL_POINT"
              :CoverageSpatialExtent {:Type "SPATIAL_POINT"}}
   :AccessConstraints [(apply str (repeat 1024 "x"))]
   :UseConstraints [(apply str (repeat 1024 "x"))]
   :ServiceQuality {:QualityFlag "Available"
                    :Lineage (apply str (repeat 100 "x"))}})

(def service-concept-1-1
  {:Coverage {:CoverageSpatialExtent {:CoverageSpatialExtentTypeType "SPATIAL_POINT"}}
   :AccessConstraints "TEST"
   :UseConstraints "TEST"
   :ServiceOrganizations [{:Roles ["SERVICE PROVIDER"]
                           :ShortName "TEST ShortName"}]
   :RelatedURLs [{:URLContentType "CollectionURL"
                  :Description "OPeNDAP Service"
                  :Type "GET SERVICE"
                  :URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"}]})

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:service ["1.0" "1.1"]}}
    (is (= [] (#'vm/version-steps :service "1.1" "1.1")))
    (is (= [["1.0" "1.1"]] (#'vm/version-steps :service "1.0" "1.1")))
    (is (= [["1.1" "1.0"]] (#'vm/version-steps :service "1.1" "1.0")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-var-generator)
            dest-version (gen/elements (v/versions :service))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :service dest-media-type metadata)))))

(deftest migrate-1_0-up-to-1_1
  (is (= service-concept-1-1
         (vm/migrate-umm {} :service "1.0" "1.1"
           {:Coverage {:Type "SPATIAL_POINT"}
            :AccessConstraints ["TEST"]
            :UseConstraints ["TEST"]
            :ServiceOrganizations [{:Roles ["SERVICE PROVIDER"]
                                    :ShortName "TEST ShortName"
                                    :Uuid "TEST Uuid"}]
            :RelatedURL {:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" :Description "OPeNDAP Service"
                         :Type "GET SERVICE"
                         :URLContentType "CollectionURL"}}))))

(deftest migrate-1_1-down-to-1_0
  (is (= service-concept-1-0
         (vm/migrate-umm {} :service "1.1" "1.0"
           {:RelatedURLs [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/" :Description "OPeNDAP Service"
                           :Type "GET SERVICE"
                           :URLContentType "CollectionURL"}]
            :AccessConstraints (apply str (repeat 4000 "x"))
            :UseConstraints (apply str (repeat 20000 "x"))
            :ServiceQuality {:QualityFlag "Available"
                             :Lineage (apply str (repeat 4000 "x"))}
            :Coverage {:CoverageSpatialExtent {:CoverageSpatialExtentTypeType
                                               "SPATIAL_POINT"}}}))))

(deftest migrate-service-options-1_1-up-to-1_2
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ServiceOptions {:SubsetTypes [ "Spatial", "Variable"]
                           :SupportedInputProjections [{:ProjectionName "Geographic"}]
                           :SupportedOutputProjections [{:ProjectionName "Geographic"}]
                           :SupportedInputFormats ["BINARY" "HDF4" "NETCDF-3" "HDF-EOS2"]
                           :SupportedOutputFormats ["BINARY" "HDF4" "NETCDF-3" "HDF-EOS2"]}
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.1" "1.2"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ServiceOptions {:SubsetTypes [ "Spatial" "Variable"]
                            :SupportedProjections [ "Geographic"]
                            :SupportedFormats ["Binary" "HDF4" "netCDF-3" "HDF-EOS4"]}
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]}))))

(deftest migrate-service-options-1_2-down-to-1_1
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ServiceOptions {:SubsetTypes [ "Spatial" "Variable"]
                           :SupportedProjections [ "Geographic"]
                           :SupportedFormats ["Binary" "HDF4" "HDF-EOS4" "HDF-EOS5"]}
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.2" "1.1"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ServiceOptions {:SubsetTypes [ "Spatial", "Variable"]
                            :SupportedInputProjections [{:ProjectionName "Geographic"}]
                            :SupportedOutputProjections [{:ProjectionName "Geographic"}]
                            :SupportedInputFormats ["BINARY" "HDF4" "HDF-EOS2" "HDF-EOS" "KML"]
                            :SupportedOutputFormats ["BINARY" "HDF4" "NETCDF-3" "HDF-EOS4"]}
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]}))))

(deftest migrate-contact-groups-1_1-up-to-1_2
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ContactGroups [{:Roles [ "INVESTIGATOR"]
                           :GroupName "I TEAM"}]
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"
                                  :ContactGroups [{:Roles [ "DEVELOPER"]
                                                   :GroupName "D TEAM"}]}]}
         (vm/migrate-umm
          {} :service "1.1" "1.2"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ContactGroups [{:Roles [ "INVESTIGATOR"]
                            :Uuid "74a1f32f-ca06-489b-bd61-4ce85872df9c"
                            :NonServiceOrganizationAffiliation "MSFC"
                            :GroupName "I TEAM"}]
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"
                                   :ContactGroups [{:Roles [ "DEVELOPER"]
                                                    :Uuid "86a1f32f-ca06-489b-bd61-4ce85872df08"
                                                    :NonServiceOrganizationAffiliation "GSFC"
                                                    :GroupName "D TEAM"}]}]}))))

(deftest migrate-contact-groups-1_2-down-to-1_1
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ContactGroups [{:Roles [ "INVESTIGATOR"]
                           :GroupName "I TEAM"}]
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"
                                  :ContactGroups [{:Roles [ "DEVELOPER"]
                                                   :GroupName "D TEAM"}]}]}
         (vm/migrate-umm
          {} :service "1.2" "1.1"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ContactGroups [{:Roles [ "INVESTIGATOR"]
                            :GroupName "I TEAM"}]
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"
                                   :ContactGroups [{:Roles [ "DEVELOPER"]
                                                    :GroupName "D TEAM"}]}]}))))

(deftest migrate-main-fields-1_1-up-to-1_2
  (is (= {:Type "OPeNDAP"
          :LongName "long name"
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.1" "1.2"
          {:Type "OPeNDAP"
           :LongName "long name"
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]
           :OnlineAccessURLPatternMatch "abc*"
           :OnlineAccessURLPatternSubstitution "dummy_pattern"
           :Coverage {:Name "dummy"}}))))

(deftest migrate-main-fields-1_2-down-to-1_1
  (is (= {:Type "WEB SERVICES"
          :LongName (apply str (repeat 120 "x"))
          :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                  :ShortName "EED2"}]}
         (vm/migrate-umm
          {} :service "1.2" "1.1"
          {:Type "ESI"
           :LongName (apply str (repeat 200 "x"))
           :ServiceOrganizations [{:Roles ["DEVELOPER"]
                                   :ShortName "EED2"}]
           :OperationMetadata []}))))

(comment

 (core/validate-metadata
  :service "application/vnd.nasa.cmr.umm+json; version=1.2"
  (slurp (io/file (io/resource "example-data/umm-json/service/v1.2/S1200245793-EDF_OPS_v1.2.json")))))
  ; (slurp (io/file (io/resource "example-data/umm-json/service/v1.2/S10000000-TEST_ORNL_WCS_v1.2.json"))))
