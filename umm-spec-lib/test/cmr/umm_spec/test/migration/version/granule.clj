(ns cmr.umm-spec.test.migration.version.granule
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.mime-types :as mt]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.umm-spec.metadata-specification :as m-spec]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.migration.version.granule :as granule]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.versioning :as v]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions
                  {:granule ["1.4" "1.5" "1.6" "1.6.1" "1.6.2" "1.6.3" "1.6.4"]}}
    (is (= [] (#'vm/version-steps :granule "1.5" "1.5")))
    (is (= [["1.4" "1.5"]] (#'vm/version-steps :granule "1.4" "1.5")))
    (is (= [["1.5" "1.4"]] (#'vm/version-steps :granule "1.5" "1.4")))
    (is (= [["1.4" "1.5"] ["1.5" "1.6"]] (#'vm/version-steps :granule "1.4" "1.6")))
    (is (= [["1.4" "1.5"] ["1.5" "1.6"] ["1.6" "1.6.1"]] (#'vm/version-steps :granule "1.4" "1.6.1")))
    (is (= [["1.6.1" "1.6.2"] ["1.6.2" "1.6.3"] ["1.6.3" "1.6.4"]] (#'vm/version-steps :granule "1.6.1" "1.6.4")))))

(defspec all-migrations-produce-valid-umm-spec 100
  (for-all [umm-record (gen/no-shrink umm-gen/umm-g-generator)
            dest-version (gen/elements (v/versions :granule))]
    (let [dest-media-type (str mt/umm-json "; version=" dest-version)
          metadata (core/generate-metadata (lkt/setup-context-for-test)
                                           umm-record dest-media-type)]
      (empty? (core/validate-metadata :granule dest-media-type metadata)))))

(deftest migrate-1_4-up-to-1_5
  (is (= {:MetadataSpecification
           {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
            :Name "UMM-G"
            :Version "1.5"}
           :DataGranule {:ArchiveAndDistributionInformation
                          [{:Name "GranuleZipFile"
                            :Size 23
                            :SizeUnit "KB"
                            :Format "ZIP"
                            :MimeType "application/x-hdf5"
                            :Files [{:Name "GranuleFileName1"
                                     :Size 10
                                     :SizeUnit "KB"
                                     :Format "NETCDF-4"
                                     :MimeType "application/x-netcdf"
                                     :FormatType "Native"}
                                    {:Name "GranuleFileName2"
                                     :Size 1
                                     :SizeUnit "KB"
                                     :Format "ASCII"
                                     :MimeType "application/x-hdf5"
                                     :FormatType "NA"}]}
                           {:Name "SupportedGranuleFileNotInPackage"
                            :Size 11
                            :SizeUnit "KB"
                            :Format "NETCDF-CF"
                            :FormatType "Supported"
                            :MimeType "application/x-netcdf"}]}
           :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                          :Type "GET SERVICE"
                          :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                          :MimeType "APPEARS"}
                         {:URL "https://example-1"
                          :Type "GET DATA"
                          :Subtype "USER FEEDBACK PAGE"}
                         {:URL "https://example-2.hdf"
                          :Type "GET DATA"
                          :Subtype "OPENDAP DATA"
                          :MimeType "application/x-hdf5"}]}
         (vm/migrate-umm {} :granule "1.4" "1.5"
                         {:DataGranule {:ArchiveAndDistributionInformation
                                        [{:Name "GranuleZipFile"
                                          :Size 23
                                          :SizeUnit "KB"
                                          :Format "ZIP"
                                          :MimeType "application/xhdf5"
                                          :Files [{:Name "GranuleFileName1"
                                                   :Size 10
                                                   :SizeUnit "KB"
                                                   :Format "NETCDF-4"
                                                   :MimeType "application/x-netcdf"
                                                   :FormatType "Native"}
                                                  {:Name "GranuleFileName2"
                                                   :Size 1
                                                   :SizeUnit "KB"
                                                   :Format "ASCII"
                                                   :MimeType "application/xhdf5"
                                                   :FormatType "NA"}]}
                                         {:Name "SupportedGranuleFileNotInPackage"
                                          :Size 11
                                          :SizeUnit "KB"
                                          :Format "NETCDF-CF"
                                          :FormatType "Supported"
                                          :MimeType "application/x-netcdf"}]}
                          :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                         :Type "GET SERVICE"
                                         :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"
                                         :MimeType "APPEARS"}
                                        {:URL "https://example-1"
                                         :Type "GET DATA"
                                         :Subtype "USER FEEDBACK"}
                                        {:URL "https://example-2.hdf"
                                         :Type "GET DATA"
                                         :Subtype "OPENDAP DATA"
                                         :MimeType "application/xhdf5"}]}))))

(deftest migrate-1_5-down-to-1_4
  (is (= {:DataGranule {:ArchiveAndDistributionInformation
                        [{:Name "GranuleZipFile"
                          :Size 23
                          :SizeUnit "KB"
                          :Format "ZIP"
                          :MimeType "application/xhdf5"
                          :Files [{:Name "GranuleFileName1"
                                   :Size 10
                                   :SizeUnit "KB"
                                   :Format "NETCDF-4"
                                   :MimeType "application/x-netcdf"
                                   :FormatType "Native"}
                                  {:Name "GranuleFileName2"
                                   :Size 1
                                   :SizeUnit "KB"
                                   :Format "ASCII"
                                   :MimeType "application/xhdf5"
                                   :FormatType "NA"}]}
                         {:Name "SupportedGranuleFileNotInPackage"
                          :Size 11
                          :SizeUnit "KB"
                          :Format "NETCDF-CF"
                          :FormatType "Supported"
                          :MimeType "application/x-netcdf"}]}
          :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                         :Type "GET SERVICE"
                         :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT"
                         :MimeType "APPEARS"}
                        {:URL "https://example-1"
                         :Type "GET DATA"
                         :Subtype "PORTAL"}
                        {:URL "https://example-2.hdf"
                         :Type "GET DATA"
                         :MimeType "application/xhdf5"}
                        {:URL "https://example-3"
                         :Type "GET DATA"
                         :Subtype "PORTAL"}]
          :SpatialExtent {:HorizontalSpatialDomain
                          {:Orbit {:AscendingCrossing 100
                                   :StartLatitude 60
                                   :StartDirection "A"
                                   :EndLatitude 80
                                   :EndDirection "A"}}}}
         (vm/migrate-umm {} :granule "1.5" "1.4"
                         {:MetadataSpecification
                           {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
                            :Name "UMM-G"
                            :Version "1.5"}
                           :DataGranule {:ArchiveAndDistributionInformation
                                         [{:Name "GranuleZipFile"
                                           :Size 23
                                           :SizeUnit "KB"
                                           :Format "ZIP"
                                           :MimeType "application/x-hdf5"
                                           :Files [{:Name "GranuleFileName1"
                                                    :Size 10
                                                    :SizeUnit "KB"
                                                    :Format "NETCDF-4"
                                                    :MimeType "application/x-netcdf"
                                                    :FormatType "Native"}
                                                   {:Name "GranuleFileName2"
                                                    :Size 1
                                                    :SizeUnit "KB"
                                                    :Format "ASCII"
                                                    :MimeType "application/x-hdf5"
                                                    :FormatType "NA"}]}
                                          {:Name "SupportedGranuleFileNotInPackage"
                                           :Size 11
                                           :SizeUnit "KB"
                                           :Format "NETCDF-CF"
                                           :FormatType "Supported"
                                           :MimeType "application/x-netcdf"}]}
                           :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                          :Type "GET SERVICE"
                                          :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                                          :MimeType "APPEARS"}
                                         {:URL "https://example-1"
                                          :Type "GET DATA"
                                          :Subtype "GoLIVE Portal"}
                                         {:URL "https://example-2.hdf"
                                          :Type "GET DATA"
                                          :Subtype "Order"
                                          :MimeType "application/x-hdf5"}
                                         {:URL "https://example-3"
                                          :Type "GET DATA"
                                          :Subtype "PORTAL"}]
                           :SpatialExtent {:HorizontalSpatialDomain
                                           {:Orbit {:AscendingCrossing 100
                                                    :StartLatitude 60
                                                    :StartDirection "A"
                                                    :EndLatitude 80
                                                    :EndDirection "A"}
                                            :Track {:Cycle 1
                                                    :Passes [{:Pass 1
                                                              :Tiles ["1L" "1R" "2F"]}
                                                             {:Pass 2
                                                              :Tiles ["3L","3R","4F"]}]}}}}))))

(deftest migrate-1_5-up-to-1_6
  (is (= {:MetadataSpecification
             {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6"
              :Name "UMM-G"
              :Version "1.6"}
           :DataGranule {:ArchiveAndDistributionInformation
                           [{:Name "GranuleZipFile"
                             :Size 23
                             :SizeUnit "KB"
                             :Format "ZIP"
                             :MimeType "application/x-hdf5"
                             :Files [{:Name "GranuleFileName1"
                                      :Size 10
                                      :SizeUnit "KB"
                                      :Format "NETCDF-4"
                                      :MimeType "application/x-netcdf"
                                      :FormatType "Native"
                                      :SizeInBytes 1024}
                                     {:Name "GranuleFileName2"
                                      :Size 1
                                      :SizeUnit "KB"
                                      :Format "ASCII"
                                      :MimeType "application/x-hdf5"
                                      :FormatType "NA"
                                      :SizeInBytes 10248592}]}
                            {:Name "SupportedGranuleFileNotInPackage"
                             :Size 11
                             :SizeUnit "KB"
                             :SizeInBytes 849324895784093
                             :Format "NETCDF-CF"
                             :FormatType "Supported"
                             :MimeType "application/x-netcdf"}]
                          :Identifiers
                           [{:Identifier "acos_LtCO2_090421_v201201_B7310A_161220013536s.nc4 and here are some extra characters so that this is too long for 1.5"
                             :IdentifierType "ProducerGranuleId"
                             :IdentifierName "Now this is the story all about how my life got flip-turned upside-down and I'd like to take a minute just sit right there"}]}
             :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                            :Type "GET SERVICE"
                            :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                            :MimeType "APPEARS"}
                           {:URL "https://example-1"
                            :Type "GET DATA"
                            :Subtype "USER FEEDBACK PAGE"}
                           {:URL "https://example-2.hdf"
                            :Type "GET DATA"
                            :Subtype "OPENDAP DATA"
                            :MimeType "application/x-hdf5"}]}
         (vm/migrate-umm {} :granule "1.5" "1.6"
                         {:MetadataSpecification
                            {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
                             :Name "UMM-G"
                             :Version "1.5"}
                          :DataGranule {:ArchiveAndDistributionInformation
                                          [{:Name "GranuleZipFile"
                                            :Size 23
                                            :SizeUnit "KB"
                                            :Format "ZIP"
                                            :MimeType "application/x-hdf5"
                                            :Files [{:Name "GranuleFileName1"
                                                     :Size 10
                                                     :SizeUnit "KB"
                                                     :Format "NETCDF-4"
                                                     :MimeType "application/x-netcdf"
                                                     :FormatType "Native"
                                                     :SizeInBytes 1024}
                                                    {:Name "GranuleFileName2"
                                                     :Size 1
                                                     :SizeUnit "KB"
                                                     :Format "ASCII"
                                                     :MimeType "application/x-hdf5"
                                                     :FormatType "NA"
                                                     :SizeInBytes 10248592}]}
                                           {:Name "SupportedGranuleFileNotInPackage"
                                            :Size 11
                                            :SizeUnit "KB"
                                            :SizeInBytes 849324895784093
                                            :Format "NETCDF-CF"
                                            :FormatType "Supported"
                                            :MimeType "application/x-netcdf"}]
                                         :Identifiers
                                          [{:Identifier "acos_LtCO2_090421_v201201_B7310A_161220013536s.nc4 and here are some extra characters so that this is too long for 1.5"
                                            :IdentifierType "ProducerGranuleId"
                                            :IdentifierName "Now this is the story all about how my life got flip-turned upside-down and I'd like to take a minute just sit right there"}]}
                            :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                           :Type "GET SERVICE"
                                           :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                                           :MimeType "APPEARS"}
                                          {:URL "https://example-1"
                                           :Type "GET DATA"
                                           :Subtype "USER FEEDBACK PAGE"}
                                          {:URL "https://example-2.hdf"
                                           :Type "GET DATA"
                                           :Subtype "OPENDAP DATA"
                                           :MimeType "application/x-hdf5"}]}))))
(deftest migrate-1_6-down-to-1_5
 (is (= {:MetadataSpecification
          {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
           :Name "UMM-G"
           :Version "1.5"}
          :DataGranule {:ArchiveAndDistributionInformation
                         [{:Name "GranuleZipFile"
                           :Size 23
                           :SizeUnit "KB"
                           :Format "ZIP"
                           :MimeType "application/x-hdf5"
                           :Files [{:Name "GranuleFileName1"
                                    :Size 10
                                    :SizeUnit "KB"
                                    :Format "NETCDF-4"
                                    :MimeType "application/x-netcdf"
                                    :FormatType "Native"}
                                   {:Name "GranuleFileName2"
                                    :Size 1
                                    :SizeUnit "KB"
                                    :Format "ASCII"
                                    :MimeType "application/x-hdf5"
                                    :FormatType "NA"}]}
                          {:Name "SupportedGranuleFileNotInPackage"
                           :Size 11
                           :SizeUnit "KB"
                           :Format "NETCDF-CF"
                           :FormatType "Supported"
                           :MimeType "application/x-netcdf"}]
                        :Identifiers
                          [{:Identifier "acos_LtCO2_090421_v201201_B7310A_161220013536s.nc4 and here are some extra chara"
                            :IdentifierType "ProducerGranuleId"
                            :IdentifierName "Now this is the story all about how my life got flip-turned upside-down and I'd "}
                           {:Identifier "And here is one without an IdentifierName"
                            :IdentifierType "ProducerGranuleId"}]}
          :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                         :Type "GET SERVICE"
                         :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                         :MimeType "APPEARS"}
                        {:URL "https://example-1"
                         :Type "GET DATA"
                         :Subtype "USER FEEDBACK PAGE"}
                        {:URL "https://example-2.hdf"
                         :Type "GET DATA"
                         :Subtype "OPENDAP DATA"
                         :MimeType "application/x-hdf5"}]}
        (vm/migrate-umm {} :granule "1.6" "1.5"
                        {:MetadataSpecification
                           {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.5"
                            :Name "UMM-G"
                            :Version "1.5"}
                         :DataGranule {:ArchiveAndDistributionInformation
                                         [{:Name "GranuleZipFile"
                                           :Size 23
                                           :SizeUnit "KB"
                                           :Format "ZIP"
                                           :MimeType "application/x-hdf5"
                                           :Files [{:Name "GranuleFileName1"
                                                    :Size 10
                                                    :SizeUnit "KB"
                                                    :Format "NETCDF-4"
                                                    :MimeType "application/x-netcdf"
                                                    :FormatType "Native"
                                                    :SizeInBytes 1024}
                                                   {:Name "GranuleFileName2"
                                                    :Size 1
                                                    :SizeUnit "KB"
                                                    :Format "ASCII"
                                                    :MimeType "application/x-hdf5"
                                                    :FormatType "NA"
                                                    :SizeInBytes 10248592}]}
                                          {:Name "SupportedGranuleFileNotInPackage"
                                           :Size 11
                                           :SizeUnit "KB"
                                           :SizeInBytes 849324895784093
                                           :Format "NETCDF-CF"
                                           :FormatType "Supported"
                                           :MimeType "application/x-netcdf"}]
                                        :Identifiers
                                         [{:Identifier "acos_LtCO2_090421_v201201_B7310A_161220013536s.nc4 and here are some extra characters so that this is too long for 1.5"
                                           :IdentifierType "ProducerGranuleId"
                                           :IdentifierName "Now this is the story all about how my life got flip-turned upside-down and I'd like to take a minute just sit right there"}
                                          {:Identifier "And here is one without an IdentifierName"
                                           :IdentifierType "ProducerGranuleId"}]}
                           :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                          :Type "GET SERVICE"
                                          :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                                          :MimeType "APPEARS"}
                                         {:URL "https://example-1"
                                          :Type "GET DATA"
                                          :Subtype "USER FEEDBACK PAGE"}
                                         {:URL "https://example-2.hdf"
                                          :Type "GET DATA"
                                          :Subtype "OPENDAP DATA"
                                          :MimeType "application/x-hdf5"}]}))))

(deftest migrate-1_6_1-down-to-1_6
 (is (= {:MetadataSpecification
          {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6"
           :Name "UMM-G"
           :Version "1.6"}
          :DataGranule {:ArchiveAndDistributionInformation
                         [{:Name "GranuleZipFile"
                           :Size 23
                           :SizeUnit "KB"
                           :Format "ZIP"
                           :MimeType "application/x-hdf5"
                           :Files [{:Name "GranuleFileName1"
                                    :Size 10
                                    :SizeUnit "KB"
                                    :Format "NETCDF-4"
                                    :MimeType "application/x-netcdf"
                                    :FormatType "Native"
                                    :SizeInBytes 1024}
                                   {:Name "GranuleFileName2"
                                    :Size 1
                                    :SizeUnit "KB"
                                    :Format "Not provided"
                                    :MimeType "Not provided"
                                    :FormatType "NA"
                                    :SizeInBytes 10248592}]}
                          {:Name "SupportedGranuleFileNotInPackage"
                           :Size 11
                           :SizeUnit "KB"
                           :SizeInBytes 849324895784093
                           :Format "Not provided"
                           :FormatType "Supported"
                           :MimeType "Not provided"}]}
          :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                         :Type "GET SERVICE"
                         :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                         :MimeType "APPEARS"}
                        {:URL "https://example-1"
                         :Type "GET DATA"
                         :Subtype "USER FEEDBACK PAGE"}
                        {:URL "https://example-2.hdf"
                         :Type "GET DATA"
                         :Subtype "OPENDAP DATA"
                         :MimeType "application/x-hdf5"}
                        {:URL "https://example-3.hdf"
                         :Type "GET DATA"
                         :Subtype "OPENDAP DATA"
                         :Format "Not provided"
                         :MimeType "Not provided"}]}
        (vm/migrate-umm {} :granule "1.6.1" "1.6"
                        {:MetadataSpecification
                           {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.1"
                            :Name "UMM-G"
                            :Version "1.6.1"}
                         :DataGranule {:ArchiveAndDistributionInformation
                                         [{:Name "GranuleZipFile"
                                           :Size 23
                                           :SizeUnit "KB"
                                           :Format "ZIP"
                                           :MimeType "application/x-hdf5"
                                           :Files [{:Name "GranuleFileName1"
                                                    :Size 10
                                                    :SizeUnit "KB"
                                                    :Format "NETCDF-4"
                                                    :MimeType "application/x-netcdf"
                                                    :FormatType "Native"
                                                    :SizeInBytes 1024}
                                                   {:Name "GranuleFileName2"
                                                    :Size 1
                                                    :SizeUnit "KB"
                                                    :Format "DMRPP"
                                                    :MimeType "application/vnd.opendap.dap4.dmrpp+xml"
                                                    :FormatType "NA"
                                                    :SizeInBytes 10248592}]}
                                          {:Name "SupportedGranuleFileNotInPackage"
                                           :Size 11
                                           :SizeUnit "KB"
                                           :SizeInBytes 849324895784093
                                           :Format "DMRPP"
                                           :FormatType "Supported"
                                           :MimeType "application/vnd.opendap.dap4.dmrpp+xml"}]}
                           :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                          :Type "GET SERVICE"
                                          :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                                          :MimeType "APPEARS"}
                                         {:URL "https://example-1"
                                          :Type "GET DATA"
                                          :Subtype "USER FEEDBACK PAGE"}
                                         {:URL "https://example-2.hdf"
                                          :Type "GET DATA"
                                          :Subtype "OPENDAP DATA"
                                          :MimeType "application/x-hdf5"}
                                         {:URL "https://example-3.hdf"
                                          :Type "GET DATA"
                                          :Subtype "OPENDAP DATA"
                                          :Format "DMRPP"
                                          :MimeType "application/vnd.opendap.dap4.dmrpp+xml"}]}))))

(deftest migrate-1_6-up-to-1_6_1
  (is (= {:MetadataSpecification
             {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.1"
              :Name "UMM-G"
              :Version "1.6.1"}
           :DataGranule {:ArchiveAndDistributionInformation
                           [{:Name "GranuleZipFile"
                             :Size 23
                             :SizeUnit "KB"
                             :Format "ZIP"
                             :MimeType "application/x-hdf5"
                             :Files [{:Name "GranuleFileName1"
                                      :Size 10
                                      :SizeUnit "KB"
                                      :Format "NETCDF-4"
                                      :MimeType "application/x-netcdf"
                                      :FormatType "Native"
                                      :SizeInBytes 1024}
                                     {:Name "GranuleFileName2"
                                      :Size 1
                                      :SizeUnit "KB"
                                      :Format "ASCII"
                                      :MimeType "application/x-hdf5"
                                      :FormatType "NA"
                                      :SizeInBytes 10248592}]}
                            {:Name "SupportedGranuleFileNotInPackage"
                             :Size 11
                             :SizeUnit "KB"
                             :SizeInBytes 849324895784093
                             :Format "NETCDF-CF"
                             :FormatType "Supported"
                             :MimeType "application/x-netcdf"}]}
             :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                            :Type "GET SERVICE"
                            :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                            :MimeType "APPEARS"}
                           {:URL "https://example-1"
                            :Type "GET DATA"
                            :Subtype "USER FEEDBACK PAGE"}
                           {:URL "https://example-2.hdf"
                            :Type "GET DATA"
                            :Subtype "OPENDAP DATA"
                            :MimeType "application/x-hdf5"}]}
         (vm/migrate-umm {} :granule "1.6" "1.6.1"
                         {:MetadataSpecification
                            {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6"
                             :Name "UMM-G"
                             :Version "1.6"}
                          :DataGranule {:ArchiveAndDistributionInformation
                                          [{:Name "GranuleZipFile"
                                            :Size 23
                                            :SizeUnit "KB"
                                            :Format "ZIP"
                                            :MimeType "application/x-hdf5"
                                            :Files [{:Name "GranuleFileName1"
                                                     :Size 10
                                                     :SizeUnit "KB"
                                                     :Format "NETCDF-4"
                                                     :MimeType "application/x-netcdf"
                                                     :FormatType "Native"
                                                     :SizeInBytes 1024}
                                                    {:Name "GranuleFileName2"
                                                     :Size 1
                                                     :SizeUnit "KB"
                                                     :Format "ASCII"
                                                     :MimeType "application/x-hdf5"
                                                     :FormatType "NA"
                                                     :SizeInBytes 10248592}]}
                                           {:Name "SupportedGranuleFileNotInPackage"
                                            :Size 11
                                            :SizeUnit "KB"
                                            :SizeInBytes 849324895784093
                                            :Format "NETCDF-CF"
                                            :FormatType "Supported"
                                            :MimeType "application/x-netcdf"}]}
                            :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                                           :Type "GET SERVICE"
                                           :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                                           :MimeType "APPEARS"}
                                          {:URL "https://example-1"
                                           :Type "GET DATA"
                                           :Subtype "USER FEEDBACK PAGE"}
                                          {:URL "https://example-2.hdf"
                                           :Type "GET DATA"
                                           :Subtype "OPENDAP DATA"
                                           :MimeType "application/x-hdf5"}]}))))

(def expected-granule-1-6-1
  {:MetadataSpecification
    {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.1"
     :Name "UMM-G"
     :Version "1.6.1"}
    :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                   :Type "GET SERVICE"
                   :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                   :MimeType "APPEARS"}
                  {:URL "https://example-1"
                   :Type "GET DATA"
                   :Subtype "USER FEEDBACK PAGE"}
                  {:URL "https://example-2.hdf"
                   :Type "GET DATA"
                   :Subtype "OPENDAP DATA"
                   :MimeType "application/x-hdf5"}
                  {:URL "s3://amazon.something.com/get-data"
                   :Type "GET DATA"
                   :Format "NETCDF-4"
                   :MimeType "application/x-netcdf"}]})

(def sample-granule-1-6-2
  {:MetadataSpecification
    {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.2"
     :Name "UMM-G"
     :Version "1.6.2"}
    :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                   :Type "GET SERVICE"
                   :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                   :MimeType "APPEARS"}
                  {:URL "https://example-1"
                   :Type "GET DATA"
                   :Subtype "USER FEEDBACK PAGE"}
                  {:URL "https://example-2.hdf"
                   :Type "GET DATA"
                   :Subtype "OPENDAP DATA"
                   :MimeType "application/x-hdf5"}
                  {:URL "s3://amazon.something.com/get-data"
                   :Type "GET DATA VIA DIRECT ACCESS"
                   :Format "NETCDF-4"
                   :MimeType "application/x-netcdf"}]})

(deftest migrate-1-6-2-down-to-1-6-1
 (is (= expected-granule-1-6-1
        (vm/migrate-umm {} :granule "1.6.2" "1.6.1" sample-granule-1-6-2))))

(deftest migrate-1-6-1-up-to-1-6-2
  (is (= {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.2"
          :Name "UMM-G"
          :Version "1.6.2"}
         (:MetadataSpecification (vm/migrate-umm {} :granule "1.6.1" "1.6.2" expected-granule-1-6-1)))))

(deftest verify-update-version
  "Check that the specify-metadata function returns the correct structure"
  (let [expected {:OtherMetadata :content-to-ignore
                  :MetadataSpecification
                  {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v0.0.0"
                   :Name "UMM-G"
                   :Version "0.0.0"}}
        base-granule {:OtherMetadata :content-to-ignore
                      :MetadataSpecification {:Name :none}}
        actual (m-spec/update-version base-granule :granule "0.0.0")]
    (is (= expected actual))))

(def sample-granule-1-6-3
  {:MetadataSpecification
    {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.3"
     :Name "UMM-G"
     :Version "1.6.3"}
    :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                   :Type "GET SERVICE"
                   :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                   :MimeType "APPEARS"}
                  {:URL "https://example-1"
                   :Type "GET DATA"
                   :Subtype "USER FEEDBACK PAGE"}
                  {:URL "https://example-2.hdf"
                   :Type "GET DATA"
                   :Subtype "OPENDAP DATA"
                   :MimeType "application/x-hdf5"}
                  {:URL "s3://amazon.something.com/get-data"
                   :Type "GET DATA VIA DIRECT ACCESS"
                   :Format "NETCDF-4"
                   :MimeType "application/x-netcdf"}
                  {:URL "s3://amazon.example.org/dmr-plus-plus"
                   :Type "EXTENDED METADATA"
                   :Subtype "DMR++"}
                  {:URL "s3://amazon.example.org/dmr-plus-plus-missing-data"
                   :Type "EXTENDED METADATA"
                   :Subtype "DMR++ MISSING DATA"}]})

(deftest migrate-1-6-2-up-to-1-6-3
  (is (= {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.3"
          :Name "UMM-G"
          :Version "1.6.3"}
         (:MetadataSpecification (vm/migrate-umm {}
                                                 :granule
                                                 "1.6.2"
                                                 "1.6.3"
                                                 sample-granule-1-6-2)))))

(deftest migrate-1-6-3-down-to-1-6-2
  "Make sure the unwanted url type is gone"
  (let [converted (vm/migrate-umm {}
                                  :granule
                                  "1.6.3"
                                  "1.6.2"
                                  sample-granule-1-6-3)
        specification (:MetadataSpecification converted)
        urls (:RelatedUrls converted)]
    (is (= {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.2"
            :Name "UMM-G"
            :Version "1.6.2"}
           specification))
    (is (= 4 (count urls)))
    (is (= [] (filter #(= "EXTENDED METADATA" (:Type %)) urls)))))

;; 1.6.4 migration tests

(def granule-1-6-4
  {:MetadataSpecification
    {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.4"
     :Name "UMM-G"
     :Version "1.6.4"}
    :DataGranule {:ArchiveAndDistributionInformation [{:Format "ASCII"} {:Format "ComicSans"}]}
    :RelatedUrls [{:URL "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/"
                   :Type "GET SERVICE"
                   :Subtype "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)"
                   :MimeType "APPEARS"
                   :Format "Future-Type"}   ;; this was a bad format
                  {:URL "s3://amazon.something.com/get-data"
                   :Type "GET DATA VIA DIRECT ACCESS"
                   :Format "NETCDF-4"       ;; this has always been a valid format
                   :MimeType "application/x-netcdf"}]})

(deftest migrate-1-6-3-up-to-1-6-4
  (let [converted (vm/migrate-umm {} :granule "1.6.3" "1.6.4" sample-granule-1-6-3)]
    (is (= {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.4"
            :Name "UMM-G"
            :Version "1.6.4"}
           (:MetadataSpecification converted))
        "Specification must be 1.6.4")
    (is (= "NETCDF-4" (:Format (nth (:RelatedUrls converted) 3))) "Confirm that existing values are not touched")))

(deftest migrate-1-6-4-down-to-1-6-3
  "Make sure the unwanted url type is gone"
  (let [converted (vm/migrate-umm {} :granule "1.6.4" "1.6.3" granule-1-6-4)
        specification (:MetadataSpecification converted)
        urls (:RelatedUrls converted)
        arch-info (get-in converted [:DataGranule :ArchiveAndDistributionInformation])]
    (is (= {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.3"
            :Name "UMM-G"
            :Version "1.6.3"}
           specification)
        "Specification must be 1.6.3")
    (is (= 2 (count urls)) "The URLs should not be dropped")
    (is (= "ASCII" (:Format (first arch-info))) "Older keyword should be mappable")
    (is (= "Not provided" (:Format (second arch-info))) "New keyword not mappable")
    (is (= "Not provided" (:Format (first urls))) "New URL format is not mappable")
    (is (= "NETCDF-4" (:Format (second urls))) "Old URL format should be mappable")))
