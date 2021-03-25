(ns cmr.umm.test.related-url-helper
  "Tests functions that categorize related urls."
  (:require
   [clojure.test :refer :all]
   [cmr.common.test.url-util :as url-util]
   [cmr.common.util :as util :refer [are3]]
   [cmr.umm.related-url-helper :as related-url-helper]
   [cmr.umm.umm-collection :as umm-c]))

(deftest guess-url-mime-type
  (testing "guess mime type based on url"
    (are3 [expected-mime-type url]
      (is (= expected-mime-type (related-url-helper/infer-url-mime-type url)))

      "test mime type in default list is properly guessed"
      "image/jpeg" "http://example.com/test/mime/type.jpeg"

      "test mime type not in default list is properly guessed"
      "application/x-netcdf" "http://example.com/test/mime/type.nc"

      "Test mime type not found returns nil"
      nil "http://example.com/test/mime/type.fake_mime_type"

      "test nil"
      nil nil)))

(deftest downloadable-mime-type
  (testing "Mime type is downloadable"
    (are3 [downloadable? mime-type]
      (is (= downloadable? (boolean (related-url-helper/downloadable-mime-type? mime-type))))

      "text/* is not downloadable (exclude whitelist)"
      false "text/html"

      "whitelisted text/* is downloadable (text/csv)"
      true "text/csv"

      "anything other than text/* is downloadable"
      true "application/json"

      "nil is not downloadable"
      false nil)))

(deftest categorize-related-urls
  (testing "categorize related urls"
    (let [downloadable-url (umm-c/map->RelatedURL
                             {:type "GET DATA"
                              :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})
          s3-url (umm-c/map->RelatedURL
                   {:type "GET DATA VIA DIRECT ACCESS"
                    :url "s3://aws.com/hydro/details"})
          browse-url (umm-c/map->RelatedURL
                       {:type "GET RELATED VISUALIZATION"
                        :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
                        :description "Some description."})
          documentation-url (umm-c/map->RelatedURL
                              {:type "VIEW RELATED INFORMATION"
                               :sub-type "USER'S GUIDE"
                               ;; CMR-4549 test if mime-type check is case insensitive.
                               :mime-type "TeXt/HtMl"
                               :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})
          metadata-url (umm-c/map->RelatedURL
                         {:type "GET SERVICE"
                          :url "http://camex.nsstc.nasa.gov/camex3/"})
          related-urls [downloadable-url s3-url browse-url documentation-url metadata-url]]
      (is (= [downloadable-url] (related-url-helper/downloadable-urls related-urls)))
      (is (= [s3-url] (related-url-helper/s3-urls related-urls)))
      (is (= [browse-url] (related-url-helper/browse-urls related-urls)))
      (is (= [documentation-url] (related-url-helper/documentation-urls related-urls)))
      (is (= [metadata-url] (related-url-helper/metadata-urls related-urls))))))

(deftest related-url->encoded-url
  (testing "encode related urls"
    (are3 [expected-url url-to-encode]
          (is (= (url-util/url->comparable-url expected-url)
                 (url-util/url->comparable-url (related-url-helper/related-url->encoded-url url-to-encode))))

          "URL with no query string"
          "https://cmr.earthdata.nasa.gov/search/collections.umm_json?"
          "https://cmr.earthdata.nasa.gov/search/collections.umm_json?"

          "Already encoded URL"
          "https://nasa.gov/search?bbox=-180%2C-90%2C180%2C90&temporal=2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z&keyword=AQUA+AIRS&keyword=AQUA+AIRS"
          "https://nasa.gov/search?bbox=-180%2C-90%2C180%2C90&temporal=2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z&keyword=AQUA%20AIRS&keyword=AQUA+AIRS"

          "Already encoded nested URL"
          "https://earthdata.nasa.gov/search?keyword=AQUA+AIRS&temporal=2005-01-01T00%3A00%3A00Z&url=https%3A%2F%2Fnasa.gov%2Fsearch%3Fbbox%3D-180%2C-90%2C180%2C90%26temporal%3D2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z%26keyword%3DAQUA+AIRS"
          "https://earthdata.nasa.gov/search?keyword=AQUA+AIRS&temporal=2005-01-01T00%3A00%3A00Z&url=https%3A%2F%2Fnasa.gov%2Fsearch%3Fbbox%3D-180%2C-90%2C180%2C90%26temporal%3D2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z%26keyword%3DAQUA+AIRS"

          "Not encoded URL"
          "https://example.org/example?bbox=-180%2C-90%2C180%2C90&temporal=2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z&keyword=AQUA+AIRS"
          "https://example.org/example?bbox=-180,-90,180,90&temporal=2005-01-01T00:00:00Z,2006-01-01T00:00:00Z&keyword=AQUA AIRS"

          "Half encoded nested URL"
          "https://nasa.gov/search?bbox=-180%2C-90%2C180%2C90&keywords=A+SAMPLE+KEY+WORD+SEARCH&URL=https%3A%2F%2Fnasa.gov%2Fsearch%3Fkeyword%3DAQUA+AIRS+AIRS%26temporal%3D2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z"
          "https://nasa.gov/search?bbox=-180,-90,180,90&keywords=A SAMPLE KEY WORD%20SEARCH&URL=https%3A%2F%2Fnasa.gov%2Fsearch%3Fkeyword%3DAQUA%20AIRS+AIRS%26temporal%3D2005-01-01T00%3A00%3A00Z%2C2006-01-01T00%3A00%3A00Z"

          "Half encoded random URL"
          "https://example.org/example?arithmetic=1%2B2%2B3%2B4&slashes=a%2Fb%2Fc&spaces=a+b+c&spaces=a+b+c&spaces=a+b+c"
          "https://example.org/example?arithmetic=1%2B2%2B3%2B4&slashes=a/b/c&spaces=a%20b%20c&spaces=a+b+c&spaces=a b c"

          "Plus signs/spaces remain spaces and arithmetic plus sign encodings remain encoded"
          "https://plus-signs.plus/addition?arithmetic=1%2B2%2B3&spaces=a+b+c&spaces=a+b+c"
          "https://plus-signs.plus/addition?arithmetic=1%2B2%2B3&spaces=a%20b%20c&spaces=a+b+c"

          "nil URL"
          nil
          nil)))
