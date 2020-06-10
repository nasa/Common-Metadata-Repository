(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.archive-and-dist-info
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.archive-and-dist-info :as arch-dist-info]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso]))

(deftest parse-archive-info-test
  "Test the archive information parsing."
  (are3 [expected archive]
    (is (= expected
           (arch-dist-info/parse-archive-info archive iso/archive-info-xpath)))

    "ISO MENDS Collection Description string"
    '({:FormatType "Supported"
       :AverageFileSize nil
       :Format "Binary"
       :TotalCollectionFileSize nil
       :FormatDescription "ABC Binary"
       :TotalCollectionFileSizeUnit nil
       :Description nil
       :AverageFileSizeUnit nil})
    (slurp (io/file (io/resource "example-data/iso19115/artificial_test_data.xml")))

    "Test with empty map"
    '()
    {}

    "Test with nil input"
    '()
    nil))

(deftest parse-dist-info-test
  "Test the distribution information parsing."
  (are3 [expected dist]
    (is (= expected
           (arch-dist-info/parse-dist-info dist iso/dist-info-xpath)))

    "ISO MENDS Collection Description string"
    '({:FormatType "Supported"
       :FormatDescription "some more notes",
       :AverageFileSize 100,
       :Fees
       "\nDigital data may be downloaded from NCEI at no charge in most cases. For custom orders of digital data or to obtain a copy of analog materials, please contact NCEI Information Services for information about current fees.\n",
       :Format "NetCDF-4",
       :TotalCollectionFileSize 1000,
       :TotalCollectionFileSizeUnit "GB",
       :Description
       "\nData may be searched and downloaded using online services provided by NCEI using the online resource URLs in this record. Contact NCEI Information Services for custom orders. When requesting data from NCEI, the desired data set may be referred to by the unique package identification number listed in this metadata record.\n",
       :AverageFileSizeUnit "MB",
       :Media ["onLine"]})
    (slurp (io/file (io/resource "example-data/iso19115/artificial_test_data.xml")))

    "Test with empty map"
    nil
    {}

    "Test with nil input"
    nil
    nil))
