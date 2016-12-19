(ns cmr.umm-spec.test.related-url
  "Tests for cmr.umm-spec.related-url functions"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.models.umm-common-models :as cmn]
   [cmr.umm-spec.related-url :as related-url]))

(deftest related-url-types
  (let [r1 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :Relation ["GET DATA"]
                                     :MimeType "application/xml"})
        r2 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :Relation ["GET RELATED VISUALIZATION"]
                                     :MimeType "Text/rtf"})
        r3 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :Relation ["VIEW RELATED INFORMATION"]
                                     :MimeType "application/json"})
        r4 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :Relation ["OPENDAP DATA ACCESS"]
                                     :MimeType "Text/csv"})
        r5 (cmn/map->RelatedUrlType {:URLs ["cmr.earthdata.nasa.gov"]
                                     :Relation ["OPENDAP DATA ACCESS" "GET RELATED VISUALIZATION"]
                                     :MimeType "Text/csv"})
        urls [r1 r2 r3 r4 r5]]

    (testing "Downloadable URLs"
      (is (= [r1] (related-url/downloadable-urls urls))))

    (testing "Browse URLs"
      (is (= [r2 r5] (related-url/browse-urls urls))))

    (testing "Resource URLs"
      (is (= [r3 r4] (related-url/resource-urls urls))))

    (testing "Atom link types"
      (is (= ["data" "browse" "metadata" "documentation" "browse"]
             (map :link-type (related-url/atom-links urls)))))))
