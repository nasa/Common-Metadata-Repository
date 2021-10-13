(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.distributions-related-url
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url :as sru]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.distributions-related-url :as smap-ru]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as mends-ru]))

(def expected-distribution-related-url-record
  "This is the normal expected value for most of the tests."
  '({:URL "http://nsidc.org/icebridge/portal/",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "Tool to visualize, search, and download IceBridge data.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL
     "https://n5eil01u.ecs.nsidc.org/ICEBRIDGE/ILATM2.002/",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description "Direct download via HTTPS protocol.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL "https://search.earthdata.nasa.gov/search?q=ILATM2",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL "http://dx.doi.org/10.5067/CPRXXK3F39RV",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "Documentation explaining the data and how it was processed.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}},
    {:URL "http://nsidc.org/icebridge/portal/2",
       :URLContentType "DistributionURL",
       :Type "GET DATA",
       :Subtype nil,
       :Description
       "Tool to visualize, search, and download IceBridge data 2.",
       :GetData
       {:Format "Not provided",
        :Size 0.0,
        :Unit "KB",
        :Fees nil,
        :Checksum nil,
        :MimeType nil}}))

(def expected-distribution-related-url-record-CMR-5366
  '({:URL "https://nsidc.org/daac/subscriptions.html",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype "Subscribe",
     :Description
     "Subscribe to have new data automatically sent when the data become available.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}}
    {:URL "https://n5eil01u.ecs.nsidc.org/MOST/MOD10A1.061/",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype "DIRECT DOWNLOAD",
     :Description "Direct download via HTTPS protocol.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}}
    {:URL "https://search.earthdata.nasa.gov/search?q=MOD10A1",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype "Earthdata Search",
     :Description
     "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}}
    {:URL "https://search.earthdata.nasa.gov/search?q=MOD10A1",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype "Earthdata Search",
     :Description
     "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data 2.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum "A12345F",
      :MimeType nil}}
    {:URL "https://search.earthdata.nasa.gov/search?q=MOD10A1",
     :URLContentType "DistributionURL",
     :Type "GET DATA",
     :Subtype nil,
     :Description
     "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data 3.",
     :GetData
     {:Format "Not provided",
      :Size 0.0,
      :Unit "KB",
      :Fees nil,
      :Checksum nil,
      :MimeType nil}}
    {:URL "https://search.earthdata.nasa.gov/search?q=MOD10A1",
     :URLContentType "CollectionURL",
     :Type "PROJECT HOME PAGE",
     :Subtype nil,
     :Description "Project Home Page."}))

(defn- distribution-related-url-iso-mends-record
  "Returns an example ISO19115 metadata record that includes multiple related urls
   for multiple transfterOptions and multiple online resources."
  []
  (slurp (io/resource "example-data/iso19115/ILATM2-test.xml")))

(defn- distribution-related-url-iso-smap-record
  "Returns an example iso smap metadata record that includes multiple related urls
   for multiple transfterOptions and multiple online resources."
  []
  (slurp (io/resource "example-data/iso-smap/SMAPExample.xml")))

(defn- distribution-related-url-iso-mends-arc-error-record
  "Returns an example iso mends record that caused internal errors while parsing.
   It contains multiple related urls."
  []
  (slurp (io/resource "example-data/error-cases/CMR-5366.xml")))

(deftest iso-mends-multiple-distributed-related-url-test
  (testing "The the software that checks multiple related urls for multiple distributors,
            multiple transferOptions, and muliple online resources."
    (let [sanitize? true
          doc (distribution-related-url-iso-mends-record)]
      (is (= expected-distribution-related-url-record
            (sru/parse-online-urls doc sanitize? mends-ru/service-url-path mends-ru/distributor-xpaths-map))))))

(deftest iso-smap-multiple-distributed-related-url-test
  (testing "The the software that checks multiple related urls for multiple distributors,
            multiple transferOptions, and muliple online resources."
    (let [sanitize? true
          doc (distribution-related-url-iso-smap-record)]
      (is (= expected-distribution-related-url-record
            (sru/parse-online-urls doc sanitize? smap-ru/service-url-path smap-ru/distributor-xpaths-map))))))

(deftest iso-mends-test-CMR-5366
  (testing "Check error case when parsing RelatedUrls that causes negative index error."
    (let [sanitize? true
          doc (distribution-related-url-iso-mends-arc-error-record)]
      (is (= expected-distribution-related-url-record-CMR-5366
             (sru/parse-online-urls doc sanitize? mends-ru/service-url-path mends-ru/distributor-xpaths-map))))))

(deftest description-string-parsing-keyword-test
  (testing (str "Parsing given string and converting it to a map then converting the left hand"
                "strings to keywords.")
    (are3 [string expected]
      (is (= expected (sru/parse-url-types-from-description string)))

      "ISO MENDS Collection Description string"
      "URLContentType: DistributionURL Description: NASA's newest search and order tool for subsetting, reprojecting, and reformatting data. Type: GET DATA Subtype: Earthdata Search"
      {:Type "GET DATA",
       :URLContentType "DistributionURL",
       :Description "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
       :Subtype "Earthdata Search"}

      "String with odd and nil values"
      ":: URLContentType:nil Checksum: \"nil\" Description: NASA's newest lawnmower ascii art: __\\.-.,,,, Type: SELF PROPELLED Subtype: Earthdata Search"
      {:Checksum "\"nil\"",
       :Description "NASA's newest lawnmower ascii art: __\\.-.,,,,",
       :Type "SELF PROPELLED",
       :Subtype "Earthdata Search"}

      "String with No pattern match"
      "This is just a plain description."
      {:Description "This is just a plain description."})))

(deftest parse-operation-description-test
  (testing "Parsing the operation description string into a map of keywords and values."
    (are3 [string expected]
      (is (= expected (#'sru/parse-operation-description string)))

      "ISO MENDS Collection Description string"
      "MimeType: Not provided DataID: OCO2_L1B_Science.7r DataType: nc4"
      {:DataID "OCO2_L1B_Science.7r",
       :MimeType "Not provided",
       :DataType "nc4"})))
