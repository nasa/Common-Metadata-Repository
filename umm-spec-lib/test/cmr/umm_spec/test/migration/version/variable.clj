(ns cmr.umm-spec.test.migration.version.variable
  (:require
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

(def variable-concept-10
  {:Name "var1"
   :LongName "variable 1"
   :Definition "first variable"
   :DataType "float"
   :Dimensions [{:Name "x" :Size 0.0}]
   :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
   :Scale 1.0
   :Offset 0
   :ScienceKeywords []
   :Services [{:ServiceTypes ["OPeNDAP"]
               :Visualizable false
               :Subsettable false}]})

(def variable-concept-11
  {:Name "var1"
   :LongName "variable 1"
   :Definition "first variable"
   :DataType "float"
   :Dimensions [{:Name "x" :Size 0.0}]
   :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
   :Scale 1.0
   :Offset 0
   :Characteristics {:StandardName "VAR_1"
                     :Reference "http://docs/ref"
                     :Bounds "Text describing coord bounds"
                     :Coordinates "Text describing coord range"
                     :GridMapping "Text describing mapping projection"
                     :Size 1024
                     :SizeUnits "bytes"
                     :ChunkSize 100
                     :Structure "/file.hdf/MODIS_Grid_Daily_1km_LST/Data_Fields/"
                     :MeasurementConditions "Sampled Particle Size Range: 90 - 600 nm"
                     :ReportingConditions "STP: 1013 mb and 273 K"}
   :Measurements [{:MeasurementName "radiative_flux"
                   :MeasurementSource "BODC"}
                  {:MeasurementName "brightness"
                   :MeasurementSource "not matched"}
                  {:MeasuremntName "length"}]})

(def variable-concept-12
  {:Name "var1"
   :LongName "variable 1"
   :Definition "first variable"
   :DataType "float"
   :Dimensions [{:Name "x" :Size 0.0 :Type "DEPTH_DIMENSION"}]
   :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
   :Scale 1.0
   :Offset 0
   :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"
                     :IndexRanges {:LatRange [-45 45]
                                   :LonRange [90 180]}}
   :MeasurementIdentifiers [{:MeasurementName {:MeasurementObject "radiative_flux"
                                               :MeasurementQuantity "incoming-sensible"}
                             :MeasurementSource "BODC"}]
   :SamplingIdentifiers [{:SamplingMethod "radiometric detection"
                          :MeasurementConditions "Sampled Particle Size Range: 90 - 600 nm"
                          :ReportingConditions "STP: 1013 mb and 273 K"}]})

(def variable-concept-13
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageSizeOfGranulesSampled 1
                           :AvgCompressionRateASCII 2
                           :AvgCompressionRateNetCDF4 3}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-14
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageSizeOfGranulesSampled 1
                           :AverageCompressionInformation [{:Format "ASCII" :Rate 2}
                                                           {:Format "NetCDF-4" :Rate 3}]}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-13-without-compression
  (merge variable-concept-12
         {:Alias "Test Alias"
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-14-without-compression
  variable-concept-13-without-compression)

(def variable-concept-14-without-ascii-and-netcdf-4-compression
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageCompressionInformation [{:Format "Test1" :Rate 2}
                                                           {:Format "Test2" :Rate 3}]}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-13-only-ascii
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageSizeOfGranulesSampled 1
                           :AvgCompressionRateASCII 2}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-14-only-ascii
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageSizeOfGranulesSampled 1
                           :AverageCompressionInformation [{:Format "ASCII" :Rate 2}]}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-13-only-netcdf-4
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageSizeOfGranulesSampled 1
                           :AvgCompressionRateNetCDF4 3}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(def variable-concept-14-only-netcdf-4
  (merge variable-concept-12
         {:Alias "Test Alias"
          :SizeEstimation {:AverageSizeOfGranulesSampled 1
                           :AverageCompressionInformation [{:Format "NetCDF-4" :Rate 3}]}
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"}}))

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:variable ["1.0" "1.1" "1.2" "1.3" "1.4"]}}
    (is (= [] (#'vm/version-steps :variable "1.2" "1.2")))
    (is (= [["1.0" "1.1"] ["1.1" "1.2"] ["1.2" "1.3"] ["1.3" "1.4"]] (#'vm/version-steps :variable "1.0" "1.4")))
    (is (= [["1.0" "1.1"]] (#'vm/version-steps :variable "1.0" "1.1")))
    (is (= [["1.1" "1.0"]] (#'vm/version-steps :variable "1.1" "1.0")))
    (is (= [["1.1" "1.2"]] (#'vm/version-steps :variable "1.1" "1.2")))
    (is (= [["1.2" "1.1"]] (#'vm/version-steps :variable "1.2" "1.1")))
    (is (= [["1.2" "1.3"]] (#'vm/version-steps :variable "1.2" "1.3")))
    (is (= [["1.3" "1.2"]] (#'vm/version-steps :variable "1.3" "1.2")))
    (is (= [["1.3" "1.4"]] (#'vm/version-steps :variable "1.3" "1.4")))
    (is (= [["1.4" "1.3"]] (#'vm/version-steps :variable "1.4" "1.3")))
    (is (= [["1.4" "1.3"] ["1.3" "1.2"] ["1.2" "1.1"] ["1.1" "1.0"]] (#'vm/version-steps :variable "1.4" "1.0")))))


(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record   (gen/no-shrink umm-gen/umm-var-generator)
            dest-version (gen/elements (v/versions :variable))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :variable dest-media-type metadata)))))

(deftest migrate-10->11
  (is (= (dissoc variable-concept-10 :Services)
         (vm/migrate-umm {} :variable "1.0" "1.1" variable-concept-10))))

(deftest migrate-11->10
  (is (= variable-concept-11
         (vm/migrate-umm {} :variable "1.1" "1.0" variable-concept-11))))

(deftest migrate-11->12
  (is (= {:Name "var1"
          :LongName "variable 1"
          :Definition "first variable"
          :DataType "float"
          :Dimensions [{:Name "x" :Size 0.0 :Type "OTHER"}]
          :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
          :Scale 1.0
          :Offset 0
          :SamplingIdentifiers [{:MeasurementConditions "Sampled Particle Size Range: 90 - 600 nm"
                                 :ReportingConditions "STP: 1013 mb and 273 K"}]
          :MeasurementIdentifiers [{:MeasurementSource "BODC"
                                    :MeasurementName {:MeasurementObject "radiative_flux"}}
                                   {:MeasurementSource "OTHER"
                                    :MeasurementName {:MeasurementObject "brightness"}}]}

         (vm/migrate-umm {} :variable "1.1" "1.2" variable-concept-11))))

(deftest migrate-12->11
  (is (= {:Name "var1"
          :LongName "variable 1"
          :Definition "first variable"
          :DataType "float"
          :Dimensions [{:Name "x" :Size 0.0}]
          :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
          :Scale 1.0
          :Offset 0
          :Measurements [{:MeasurementName "radiative_flux"
                          :MeasurementSource "BODC"}]}
         (vm/migrate-umm {} :variable "1.2" "1.1" variable-concept-12))))

(deftest mgrate-12->13
  (is (= {:Dimensions [{:Name "x" :Size 0.0 :Type "DEPTH_DIMENSION"}]
          :Scale 1.0
          :Offset 0
          :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
          :SamplingIdentifiers [{:SamplingMethod "radiometric detection"
                                 :MeasurementConditions
                                 "Sampled Particle Size Range: 90 - 600 nm"
                                 :ReportingConditions "STP: 1013 mb and 273 K"}]
          :Definition "first variable"
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"
                            :IndexRanges {:LatRange [-45 45] :LonRange [90 180]}}
          :Name "var1"
          :MeasurementIdentifiers [{:MeasurementName {:MeasurementObject "radiative_flux"
                                                      :MeasurementQuantity "incoming-sensible"}
                                    :MeasurementSource "BODC"}]
          :LongName "variable 1"
          :DataType "float"}
         (vm/migrate-umm {} :variable "1.2" "1.3" variable-concept-12))))

(deftest migrate-13->12
  (is (= {:Dimensions [{:Name "x" :Size 0.0 :Type "DEPTH_DIMENSION"}]
          :Scale 1.0
          :Offset 0
          :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
          :SamplingIdentifiers [{:SamplingMethod "radiometric detection"
                                 :MeasurementConditions
                                 "Sampled Particle Size Range: 90 - 600 nm"
                                 :ReportingConditions "STP: 1013 mb and 273 K"}]
          :Definition "first variable"
          :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"
                            :IndexRanges {:LatRange [-45 45] :LonRange [90 180]}}
          :Name "var1"
          :MeasurementIdentifiers [{:MeasurementName
                                    {:MeasurementObject "radiative_flux"
                                     :MeasurementQuantity "incoming-sensible"}
                                    :MeasurementSource "BODC"}]
          :LongName "variable 1"
          :DataType "float"}
         (vm/migrate-umm {} :variable "1.3" "1.2" (assoc-in variable-concept-13
                                                            [:Characteristics]
                                                            {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"
                                                             :IndexRanges {:LatRange [-45 45]
                                                                           :LonRange [90 180]}})))))

(deftest migrate-13->14
  (is (= variable-concept-14
         (vm/migrate-umm {} :variable "1.3" "1.4" variable-concept-13))))

(deftest migrate-13->14-without-compression
  (is (= variable-concept-14-without-compression
         (vm/migrate-umm {} :variable "1.3" "1.4" variable-concept-13-without-compression))))

(deftest migrate-13->14-only-ascii
  (is (= variable-concept-14-only-ascii
         (vm/migrate-umm {} :variable "1.3" "1.4" variable-concept-13-only-ascii))))

(deftest migrate-13->14-only-netcdf-4
  (is (= variable-concept-14-only-netcdf-4
         (vm/migrate-umm {} :variable "1.3" "1.4" variable-concept-13-only-netcdf-4))))

(deftest migrate-14->13
  (is (= variable-concept-13
         (vm/migrate-umm {} :variable "1.4" "1.3" variable-concept-14))))

(deftest migrate-14->13-without-compression
  (is (= variable-concept-13-without-compression
         (vm/migrate-umm {} :variable "1.4" "1.3" variable-concept-14-without-compression))))

(deftest migrate-14->13-without-ascii-and-netcdf-4-compression
  (is (= variable-concept-13-without-compression
         (vm/migrate-umm {} :variable "1.4" "1.3" variable-concept-14-without-ascii-and-netcdf-4-compression))))

(deftest migrate-14->13-only-ascii
  (is (= variable-concept-13-only-ascii
         (vm/migrate-umm {} :variable "1.4" "1.3" variable-concept-14-only-ascii))))

(deftest migrate-14->13-only-netcdf-4
  (is (= variable-concept-13-only-netcdf-4
         (vm/migrate-umm {} :variable "1.4" "1.3" variable-concept-14-only-netcdf-4))))

(deftest migrate-14->15
  (is (= (assoc variable-concept-14 :AcquisitionSourceName "Not Provided")
         (vm/migrate-umm {} :variable "1.4" "1.5" variable-concept-14))))

(deftest migrate-15->14
  (is (= variable-concept-14
         (vm/migrate-umm {} :variable "1.5" "1.4"
                         (assoc variable-concept-14  :AcquisitionSourceName "OMI")))))

(def variable-concept-15
  {:Name "var1"
   :LongName "variable 1"
   :Definition "first variable"
   :DataType "float"
   :Dimensions [{:Name "x" :Size 0.0 :Type "DEPTH_DIMENSION"}
                {:Name "y" :Size 0.0 :Type "OTHER"}]
   :Sets [{:Name "empty" :Type "general" :Size 0 :Index 0}]
   :Scale 1.0
   :Offset 0
   :Characteristics {:GroupPath "/MODIS_Grid_Daily_1km_LST/Data_Fields"
                     :IndexRanges {:LatRange [-45 45]
                                   :LonRange [90 180]}}
   :MeasurementIdentifiers [{:MeasurementName {:MeasurementObject "radiative_flux"
                                               :MeasurementQuantity "incoming-sensible"}
                             :MeasurementSource "BODC"}
                            {:MeasurementName {:MeasurementObject "brightness"}
                             :MeasurementSource "OTHER"}
                            {:MeasurementName {:MeasurementQuantity "blinding"}
                             :MeasurementSource "OTHER"}]
   :SamplingIdentifiers [{:SamplingMethod "radiometric detection"
                          :MeasurementConditions "Sampled Particle Size Range: 90 - 600 nm"
                          :ReportingConditions "STP: 1013 mb and 273 K"}]})

(deftest migrate-15->16
  (is (= (assoc variable-concept-15
                :MeasurementIdentifiers [{:MeasurementContextMedium "not_specified"
                                          :MeasurementObject "radiative_flux"
                                          :MeasurementQuantities [{:Value "incoming-sensible"}]}
                                         {:MeasurementContextMedium "not_specified"
                                          :MeasurementObject "brightness"}
                                         {:MeasurementContextMedium "not_specified"
                                          :MeasurementObject "not_specified"
                                          :MeasurementQuantities [{:Value "blinding"}]}])
         (vm/migrate-umm {} :variable "1.5" "1.6" variable-concept-15))))

(deftest migrate-16->15
  (is (= (assoc variable-concept-15
                :MeasurementIdentifiers [{:MeasurementName {:MeasurementObject "Brightness"
                                                            :MeasurementQuantity "Temperature"}
                                          :MeasurementSource "OTHER"}
                                         {:MeasurementName {:MeasurementObject "radiative_flux"}
                                          :MeasurementSource "OTHER"}])
         (vm/migrate-umm {} :variable "1.6" "1.5"
                         (assoc variable-concept-15
                                :Dimensions [{:Name "x" :Size 0.0 :Type "DEPTH_DIMENSION"}
                                             {:Name "y" :Size 0.0 :Type "CROSS_TRACK_DIMENSION"}]
                                :MeasurementIdentifiers
                                [{:MeasurementContextMedium "Atmosphere"
                                  :MeasurementContextMediumURI "http://purl.obolibrary.org/obo/ENVO_01000810"
                                  :MeasurementObject "Brightness"
                                  :MeasurementQuantities
                                  [{:Value "Temperature"
                                    :MeasurementQuantityURI "http://www.ontobee.org/ontology/PATO?iri=http://purl.obolibrary.org/obo/PATO_0000146"}
                                   {:Value "lumen"
                                    :MeasurementQuantityURI "http://www.ontobee.org/ontology/lumen"}]}
                                 {:MeasurementContextMedium "Surface"
                                  :MeasurementContextMediumURI "http://purl.obolibrary.org/obo/ENVO_01000811"
                                  :MeasurementObject "radiative_flux"}])))))
