(ns cmr.ingest.services.granule-bulk-update.additional-file.umm-g-test
  "Unit tests for UMM-G additionalfile update"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.additional-file.umm-g :as umm-g]))

(def ^:private context "A fake context object" {:ignore-kms-keywords true})

(deftest update-files
  ;;note we are not validating values here, so these tests serve solely to verify
  ;;replacement logic. Proper validation is done as part of integration testing
  (testing "Add/update/remove AdditionalFile Size, SizeInBytes, and SizeUnit"
    (are3 [input-files source result]
      (is (= result
             (get-in (umm-g/update-additional-files
                      context
                      {:DataGranule {:ArchiveAndDistributionInformation source}}
                      input-files false)
                     [:DataGranule :ArchiveAndDistributionInformation])))

      "Add SizeInBytes to a File or Package"
      [{:Name "granFile1" :SizeInBytes 1000}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4"
        :MimeType "application/x-netcdf"
        :FormatType "NA"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB" :SizeInBytes 1000
        :Format "NETCDF-4"
        :MimeType "application/x-netcdf"
        :FormatType "NA"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]

      "Update SizeInBytes in a File or Package"
      [{:Name "granFile2" :SizeInBytes 100}]
      [{:Name "granFile1"
        :SizeInBytes 200
        :Format "GZIP"
        :MimeType "application/gzip"}
       {:Name "granFile2"
        :SizeInBytes 50
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]
      [{:Name "granFile1"
        :SizeInBytes 200
        :Format "GZIP"
        :MimeType "application/gzip"}
       {:Name "granFile2"
        :SizeInBytes 100
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]

      "Remove SizeInBytes in a File or Package"
      [{:Name "granFile1" :SizeInBytes 0}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB" :SizeInBytes 1000
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]

      "Add Size and SizeUnit to a File or Package"
      [{:Name "granFile1" :Size 100 :SizeUnit "MB"}]
      [{:Name "granFile1"
        :Format "CSV"}]
      [{:Name "granFile1"
        :Size 100 :SizeUnit "MB"
        :Format "CSV"}]

      "Update only Size in a File or Package"
      [{:Name "granFile1" :Size 150}]
      [{:Name "granFile1"
        :Size 100 :SizeUnit "MB"
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]
      [{:Name "granFile1"
        :Size 150 :SizeUnit "MB"
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]

      "Update only SizeUnit in a File or Package"
      [{:Name "granFile1" :SizeUnit "KB"}]
      [{:Name "granFile1"
        :Size 100 :SizeUnit "MB"
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]
      [{:Name "granFile1"
        :Size 100 :SizeUnit "KB"
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]

      "Update both Size and SizeUnit in a File or Package"
      [{:Name "granFile1" :Size 1 :SizeUnit "GB"}]
      [{:Name "granFile1"
        :Size 1000 :SizeUnit "MB"
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "GB"
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]

      "Remove Size and SizeUnit in a File or Package, also update SizeInBytes"
      [{:Name "granFile1" :Size 0 :SizeUnit "KB" :SizeInBytes 100000}]
      [{:Name "granFile1"
        :Size 10 :SizeUnit "KB" :SizeInBytes 10000
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]
      [{:Name "granFile1"
        :SizeInBytes 100000
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]

      "Remove Size and SizeUnit in a File or Package, not specifying SizeUnit"
      [{:Name "granFile1" :Size 0}]
      [{:Name "granFile1"
        :Size 10 :SizeUnit "KB" :SizeInBytes 10000
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]
      [{:Name "granFile1"
        :SizeInBytes 10000
        :Checksum {:Value "123ABC" :Algorithm "MD5"}}]

      "Update Size, SizeUnit, and add SizeInBytes in a File within a FilePackage"
      [{:Name "granFile2" :SizeInBytes 2000 :Size 2 :SizeUnit "KB"}]
      [{:Name "granFile1"
        :Format "HDF5"
        :Size 2 :SizeUnit "GB"}
       {:Name "GranZipFile"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4"
        :MimeType "application/x-netcdf"
        :Files [{:Name "granFile2"
                 :FormatType "Supported"
                 :Size 1 :SizeUnit "KB"}]}]
      [{:Name "granFile1"
        :Format "HDF5"
        :Size 2 :SizeUnit "GB"}
       {:Name "GranZipFile"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4"
        :MimeType "application/x-netcdf"
        :Files [{:Name "granFile2"
                 :FormatType "Supported"
                 :SizeInBytes 2000 :Size 2 :SizeUnit "KB"}]}]))

  (testing "Add/update AdditionalFile Format, FormatType, and MimeType"
    (are3 [input-files source result]
      (is (= result
             (get-in (umm-g/update-additional-files
                      context
                      {:DataGranule {:ArchiveAndDistributionInformation source}}
                      input-files false)
                     [:DataGranule :ArchiveAndDistributionInformation])))

      "Add Format to a File or Package"
      [{:Name "granFile1" :Format "ASCII"}]
      [{:Name "granFile1"}]
      [{:Name "granFile1"
        :Format "ASCII"}]

      "Add FormatType to a File or Package"
      [{:Name "granFile1" :FormatType "Native"}]
      [{:Name "granFile1"}]
      [{:Name "granFile1"
        :FormatType "Native"}]

      "Add MimeType to a File or Package"
      [{:Name "granFile1" :MimeType "application/x-netcdf"}]
      [{:Name "granFile1"
        :Format "BINARY"}]
      [{:Name "granFile1"
        :Format "BINARY"
        :MimeType "application/x-netcdf"}]

      "Update Format in a File or Package"
      [{:Name "granFile1" :Format "GZIP"}]
      [{:Name "granFile1"
        :Size 30 :SizeUnit "TB"
        :FormatType "NA"
        :MimeType "text/plain"
        :Format "BINARY"
        :Checksum {:Value "boguschecksum" :Algorithm "Adler-32"}}]
      [{:Name "granFile1"
        :Size 30 :SizeUnit "TB"
        :FormatType "NA"
        :MimeType "text/plain"
        :Format "GZIP"
        :Checksum {:Value "boguschecksum" :Algorithm "Adler-32"}}]

      "Update FormatType in a File or Package"
      [{:Name "granFile1" :FormatType "Supported"}]
      [{:Name "granFile1"
        :Size 30 :SizeUnit "TB"
        :FormatType "NA"
        :MimeType "text/plain"
        :Format "BINARY"
        :Checksum {:Value "boguschecksum" :Algorithm "Adler-32"}}]
      [{:Name "granFile1"
        :Size 30 :SizeUnit "TB"
        :FormatType "Supported"
        :MimeType "text/plain"
        :Format "BINARY"
        :Checksum {:Value "boguschecksum" :Algorithm "Adler-32"}}]


      "Update MimeType in a File or Package"
      [{:Name "granFile1" :MimeType "image/jpeg"}]
      [{:Name "granFile1"
        :Size 30 :SizeUnit "TB"
        :FormatType "NA"
        :MimeType "text/plain"
        :Format "BINARY"
        :Checksum {:Value "boguschecksum" :Algorithm "Adler-32"}}]
      [{:Name "granFile1"
        :Size 30 :SizeUnit "TB"
        :FormatType "NA"
        :MimeType "image/jpeg"
        :Format "BINARY"
        :Checksum {:Value "boguschecksum" :Algorithm "Adler-32"}}]

      "Add MimeType, Format, FormatType to a File in a FilePackage"
      [{:Name "granFile2" :Format "GIF" :FormatType "Native" :MimeType "image/jpeg"}]
      [{:Name "GranZipFile"
        :Size 5 :SizeUnit "KB"
        :Format "NETCDF-4"
        :MimeType "application/x-netcdf"
        :Files [{:Name "granFile2"
                 :Size 1 :SizeUnit "KB"}]}]
      [{:Name "GranZipFile"
        :Size 5 :SizeUnit "KB"
        :Format "NETCDF-4"
        :MimeType "application/x-netcdf"
        :Files [{:Name "granFile2"
                 :Size 1 :SizeUnit "KB"
                 :Format "GIF"
                 :FormatType "Native"
                 :MimeType "image/jpeg"}]}]))

  (testing "Add/update checksum value and algorithm"
    (are3 [input-files source result]
      (is (= result
             (get-in (umm-g/update-additional-files
                      context
                      {:DataGranule {:ArchiveAndDistributionInformation source}}
                      input-files false)
                     [:DataGranule :ArchiveAndDistributionInformation])))

      "Add a checksum"
      [{:Name "granFile1" :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]

      "Update Checksum value"
      [{:Name "granFile1" :Checksum {:Value "newFoobar"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "newFoobar" :Algorithm "SHA-256"}}]

      "Update Checksum value and algorithm"
      [{:Name "granFile1" :Checksum {:Value "ASDF123" :Algorithm "MD5"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "foobar1234" :Algorithm "SHA-256"}}]
      [{:Name "granFile1"
        :Size 1 :SizeUnit "KB"
        :Format "NETCDF-4" :FormatType "NA"
        :MimeType "application/x-netcdf"
        :Checksum {:Value "ASDF123" :Algorithm "MD5"}}])))
