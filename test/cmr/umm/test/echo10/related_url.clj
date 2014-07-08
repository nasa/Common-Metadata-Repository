(ns cmr.umm.test.echo10.related-url
  "Tests functions that categorize related urls."
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.related-url :as ru]
            [cmr.umm.collection :as umm-c]))

(deftest categorize-related-urls
  (testing "categorize related urls"
    (let [downloadable-urls [(umm-c/map->RelatedURL
                               {:type "GET DATA"
                                :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})]
          browse-urls [(umm-c/map->RelatedURL
                         {:type "GET RELATED VISUALIZATION"
                          :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
                          :description "Some description."})]
          documentation-urls [(umm-c/map->RelatedURL
                                {:type "VIEW RELATED INFORMATION"
                                 :sub-type "USER'S GUIDE"
                                 :mime-type "Text/html"
                                 :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})]
          metadata-urls [(umm-c/map->RelatedURL
                           {:type "GET SERVICE"
                            :url "http://camex.nsstc.nasa.gov/camex3/"})]
          related-urls (flatten (conj downloadable-urls browse-urls documentation-urls metadata-urls))]
      (is (and (= downloadable-urls (ru/downloadable-urls related-urls))
               (= browse-urls (ru/browse-urls related-urls))
               (= documentation-urls (ru/documentation-urls related-urls))
               (= metadata-urls (ru/metadata-urls related-urls)))))))
