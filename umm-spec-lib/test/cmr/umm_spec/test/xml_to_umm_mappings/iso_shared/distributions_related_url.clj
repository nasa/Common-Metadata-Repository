(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.distributions-related-url
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
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
