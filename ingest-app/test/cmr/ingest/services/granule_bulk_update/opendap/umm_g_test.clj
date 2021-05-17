(ns cmr.ingest.services.granule-bulk-update.opendap.umm-g-test
  "Unit tests for UMM-G OPeNDAP url update"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]
   [cmr.ingest.services.granule-bulk-update.opendap.umm-g :as umm-g]))

(def ^:private doc-related-url
  "Sample document RelatedUrl"
  {:URL "http://example.com/doc.html"
   :Type "VIEW RELATED INFORMATION"
   :Subtype "USER'S GUIDE"
   :Description "ORNL DAAC Data Set Documentation"
   :Format "HTML"
   :MimeType "text/html"})

(def ^:private sample-urls
  "Sample RelatedUrls with both cloud and on-prem OPeNDAP urls and other url"
  [doc-related-url
   {:URL "http://example.com/to_be_updated"
    :Type "GET DATA"
    :Subtype "OPENDAP DATA"
    :Description "on-prem OPeNDAP Documentation"}
   {:URL "https://opendap.uat.earthdata.nasa.gov/to_be_updated"
    :Type "GET DATA"
    :Subtype "OPENDAP DATA"
    :Description "cloud OPeNDAP Documentation"}])

(deftest update-opendap-url
  (testing "add or update OPeNDAP url to UMM-G"
    (are3 [url-value source result]
      (let [grouped-urls (opendap-util/validate-url url-value)]
        (is (= result
               (umm-g/update-opendap-url source grouped-urls))))

      "no RelatedUrls in metadata, on-prem url update"
      "http://example.com/foo"
      {}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}]}

      "no RelatedUrls in metadata, cloud url update"
      "https://opendap.earthdata.nasa.gov/foo"
      {}
      {:RelatedUrls [{:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}]}

      "no RelatedUrls in metadata, cloud url and on-prem url update"
      "http://example.com/foo, https://opendap.earthdata.nasa.gov/foo"
      {}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}]}

      "non-matching RelatedUrls in metadata, on-prem url update"
      "http://example.com/foo"
      {:RelatedUrls [doc-related-url]}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     doc-related-url]}

      "non-matching RelatedUrls in metadata, cloud url update"
      "https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls [doc-related-url]}
      {:RelatedUrls [{:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     doc-related-url]}

      "non-matching RelatedUrls in metadata, cloud url and on-prem url update"
      "http://example.com/foo, https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls [doc-related-url]}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     doc-related-url]}

      "matching RelatedUrls in metadata, on-prem url update"
      "http://example.com/foo"
      {:RelatedUrls sample-urls}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "on-prem OPeNDAP Documentation"}
                     {:URL "https://opendap.uat.earthdata.nasa.gov/to_be_updated"
                      :Type "GET DATA"
                      :Subtype "OPENDAP DATA"
                      :Description "cloud OPeNDAP Documentation"}
                     doc-related-url]}

      "matching RelatedUrls in metadata, cloud url update"
      "https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls sample-urls}
      {:RelatedUrls [{:URL "http://example.com/to_be_updated"
                      :Type "GET DATA"
                      :Subtype "OPENDAP DATA"
                      :Description "on-prem OPeNDAP Documentation"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "cloud OPeNDAP Documentation"}
                     doc-related-url]}

      "matching RelatedUrls in metadata, cloud url and on-prem url update"
      "http://example.com/foo, https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls sample-urls}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "on-prem OPeNDAP Documentation"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "cloud OPeNDAP Documentation"}
                     doc-related-url]})))

(deftest append-opendap-url
  (testing "append missing url or fail if existing when adding OPeNDAP url to UMM-G"
    (are3 [url-value source result]
      (let [grouped-urls (opendap-util/validate-url url-value)]
        (is (= result
               (umm-g/append-opendap-url source grouped-urls))))

      "no RelatedUrls in metadata, on-prem url update"
      "http://example.com/foo"
      {}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}]}

      "no RelatedUrls in metadata, cloud url update"
      "https://opendap.earthdata.nasa.gov/foo"
      {}
      {:RelatedUrls [{:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}]}

      "no RelatedUrls in metadata, cloud url and on-prem url update"
      "http://example.com/foo, https://opendap.earthdata.nasa.gov/foo"
      {}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}]}

      "non-matching RelatedUrls in metadata, on-prem url update"
      "http://example.com/foo"
      {:RelatedUrls [doc-related-url]}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     doc-related-url]}

      "non-matching RelatedUrls in metadata, cloud url update"
      "https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls [doc-related-url]}
      {:RelatedUrls [{:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     doc-related-url]}

      "non-matching RelatedUrls in metadata, cloud url and on-prem url update"
      "http://example.com/foo, https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls [doc-related-url]}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"}
                     doc-related-url]}

      "matching RelatedUrls in metadata, on-prem url update"
      "http://example.com/foo"
      {:RelatedUrls sample-urls}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "on-prem OPeNDAP Documentation"}
                     {:URL "https://opendap.uat.earthdata.nasa.gov/to_be_updated"
                      :Type "GET DATA"
                      :Subtype "OPENDAP DATA"
                      :Description "cloud OPeNDAP Documentation"}
                     doc-related-url]}

      "matching RelatedUrls in metadata, cloud url update"
      "https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls sample-urls}
      {:RelatedUrls [{:URL "http://example.com/to_be_updated"
                      :Type "GET DATA"
                      :Subtype "OPENDAP DATA"
                      :Description "on-prem OPeNDAP Documentation"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "cloud OPeNDAP Documentation"}
                     doc-related-url]}

      "matching RelatedUrls in metadata, cloud url and on-prem url update"
      "http://example.com/foo, https://opendap.earthdata.nasa.gov/foo"
      {:RelatedUrls sample-urls}
      {:RelatedUrls [{:URL "http://example.com/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "on-prem OPeNDAP Documentation"}
                     {:URL "https://opendap.earthdata.nasa.gov/foo"
                      :Type "USE SERVICE API"
                      :Subtype "OPENDAP DATA"
                      :Description "cloud OPeNDAP Documentation"}
                     doc-related-url]})))
