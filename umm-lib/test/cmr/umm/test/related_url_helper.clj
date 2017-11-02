(ns cmr.umm.test.related-url-helper
  "Tests functions that categorize related urls."
  (:require [clojure.test :refer :all]
            [cmr.umm.related-url-helper :as ru]
            [cmr.umm.umm-collection :as umm-c]))

(deftest categorize-related-urls
  (testing "categorize related urls"
    (let [downloadable-url (umm-c/map->RelatedURL
                             {:type "GET DATA"
                              :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})
          browse-url (umm-c/map->RelatedURL
                       {:type "GET RELATED VISUALIZATION"
                        :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
                        :description "Some description."})
          documentation-url (umm-c/map->RelatedURL
                              {:type "VIEW RELATED INFORMATION"
                               :sub-type "USER'S GUIDE"
                               :mime-type "TeXt/HtMl"
                               :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})
          metadata-url (umm-c/map->RelatedURL
                         {:type "GET SERVICE"
                          :url "http://camex.nsstc.nasa.gov/camex3/"})
          related-urls [downloadable-url browse-url documentation-url metadata-url]]
      (is (= [downloadable-url] (ru/downloadable-urls related-urls)))
      (is (= [browse-url] (ru/browse-urls related-urls)))
      (is (= [documentation-url] (ru/documentation-urls related-urls)))
      (is (= [metadata-url] (ru/metadata-urls related-urls))))))
