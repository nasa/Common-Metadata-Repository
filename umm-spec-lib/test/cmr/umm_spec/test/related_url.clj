(ns cmr.umm-spec.test.related-url
  "Tests for cmr.umm-spec.related-url functions"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.dif-util :as dif-util]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.related-url :as related-url]
   [cmr.umm-spec.util :as su]))

(deftest related-url-key-value-mapping
  (is (= {:URLContentType "DistributionURL" :Type "GET DATA" :Subtype "Earthdata Search"}
         (get dif-util/dif-url-content-type->umm-url-types ["GET DATA" "Earthdata Search"] su/default-url-type))))

(deftest related-url->title
  (are3 [expected content-type type subtype]
        (is (= expected
               (related-url/related-url->title
                  (cmn/map->RelatedUrlType {:URL "http://example.com"
                                            :URLContentType content-type
                                            :Type type
                                            :Subtype subtype}))))

        "DistributionURL/GET DATA/APPEEARS"
        "Download this dataset through APPEEARS" "DistributionURL" "GET DATA" "APPEEARS"

        "VisualizationURL/GET RELATED VISUALIZATION with nil subtype should return default"
        "Get a related visualization" "VisualizationURL" "GET RELATED VISUALIZATION" nil

        "No match should return nil"
        nil "CollectionURL" "USE SERVICE API" nil

        "URLContentType and Type are valid but Subtype does not map to anything so it should return the default value"
        "Visit this dataset's data center's home page" "DataCenterURL" "HOME PAGE" "PUBLICATIONS"))

(deftest related-url-types
  (let [r1 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :URLContentType "DistributionURL"
                                     :Type "GET DATA"
                                     :MimeType "application/xml"})
        r2 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :URLContentType "VisualizationURL"
                                     :Type "GET RELATED VISUALIZATION"
                                     :MimeType "Text/rtf"})
        r3 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :URLContentType "PublicationURL"
                                     :Type "VIEW RELATED INFORMATION"
                                     :MimeType "application/json"})
        r4 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :URLContentType "DistributionURL"
                                     :Type "USE SERVICE API"
                                     :Subtype "OPENDAP DATA"
                                     :MimeType "Text/csv"})
        r5 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :URLContentType "VisualizationURL"
                                     :Type "GET RELATED VISUALIZATION"
                                     :Subtype "MAP"
                                     :MimeType "Text/csv"})
        r6 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :URLContentType "DistributionURL"
                                     :Type "GET CAPABILITIES"
                                     :Subtype "OpenSearch"
                                     :MimeType "application/opensearchdescription+xml"})
        urls [r1 r2 r3 r4 r5 r6]]

    (testing "Downloadable URLs"
      (is (= [r1] (related-url/downloadable-urls urls))))

    (testing "Browse URLs"
      (is (= [r2 r5] (related-url/browse-urls urls))))

    (testing "Resource URLs"
      (is (= [r3 r4 r6] (related-url/resource-urls urls))))

    (testing "Atom link types"
      (is (= ["data" "browse" "documentation" "service" "browse" "search"]
             (map :link-type (related-url/atom-links urls)))))))
