(ns cmr.search.test.results-handlers.stac-results-handler
  "This tests the stac-results-handler namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.results-handlers.stac-results-handler :as stac-results-handler]))

(def ^:private metadata-link
  "example metadata-link for test"
  "https://example.com/metadata-link")

(deftest atom-links->assets
  (testing "Atom links to STAC assets"
    (are3 [links assets]
      (let [expected-assets (merge assets
                                   {:metadata {:href metadata-link
                                               :type "application/xml"}})]
        (is (= expected-assets
               (#'stac-results-handler/atom-links->assets metadata-link links))))

      "no extra links"
      nil
      nil

      "single data link"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
        :link-type "data"
        :title "example data"
        :mime-type "application/x-hdfeos"}]
      {:data {:title "example data"
              :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
              :type "application/x-hdfeos"}}

      "multiple data links"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
        :link-type "data"
        :title "example data"
        :mime-type "application/x-hdfeos"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data11"
        :link-type "data"
        :mime-type "application/gzip"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data99"
        :link-type "data"
        :mime-type "application/x-hdf"}]
      {:data {:title "example data"
              :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
              :type "application/x-hdfeos"}
       :data1 {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data11"
               :type "application/gzip"}
       :data2 {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data99"
               :type "application/x-hdf"}}

      "single browse link with valid mime-type"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/tiff"}}

      "single browse link with valid mime-type to show mime-type has precedence over href suffix."
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.jpeg"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.jpeg"
                :type "image/tiff"}}

      "single browse link without mime-type"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/jpeg"}}

      "single browse link with invalid mime-type, type is determined by href suffix"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.tif"
        :link-type "browse"
        :title "example browse"
        :mime-type "image"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.tif"
                :type "image/tiff"}}

      "multiple browse links, only the first one is used"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse1"
        :link-type "browse"
        :mime-type "image/jpeg"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/tiff"}}

      "single opendap link"
      [{:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
        :link-type "service"
        :title "opendap link"
        :mime-type "text/xml"}]
      {:opendap {:title "opendap link"
                 :href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
                 :type "text/xml"}}

      "multiple opendap links, only the first one is used"
      [{:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
        :link-type "service"
        :title "opendap link"}
       {:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl2"
        :link-type "service"
        :title "opendap link2"}]
      {:opendap {:title "opendap link"
                 :href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"}}

      "multiple type of links"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
        :link-type "data"
        :title "example data"
        :mime-type "application/x-hdfeos"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}
       {:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
        :link-type "service"
        :title "opendap link"
        :mime-type "text/xml"}]
      {:data {:title "example data"
              :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
              :type "application/x-hdfeos"}
       :browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/tiff"}
       :opendap {:title "opendap link"
                 :href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
                 :type "text/xml"}})))
