(ns cmr.umm-spec.test.migration.version.granule
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

(deftest test-version-steps
  (with-bindings {#'cmr.umm-spec.versioning/versions {:granule ["1.4" "1.5"]}}
    (is (= [] (#'vm/version-steps :granule "1.5" "1.5")))
    (is (= [["1.4" "1.5"]] (#'vm/version-steps :granule "1.4" "1.5")))
    (is (= [["1.5" "1.4"]] (#'vm/version-steps :granule "1.5" "1.4")))))

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
                         :Subtype "PORTAL"}]}
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
                           :Track {:Cycle 1
                                   :Passes [{:Pass 1
                                             :Tiles ["1L" "1R" "2F"]}
                                            {:Pass 2
                                             :Tiles ["3L","3R","4F"]}]}}))))
