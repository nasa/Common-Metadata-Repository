(ns cmr.search.test.site.data
  (:require [clojure.test :refer :all]
            [cmr.search.site.data :as data]))

(deftest doi-link
  (testing "generate a link from doi data"
    (let [data {"DOI" "doi:10.7265/N5R78C49"}]
      (is (= (data/doi-link data)
             "http://dx.doi.org/doi:10.7265/N5R78C49")))))

(deftest cmr-link
  (testing "generate a cmr landing page from a host and a concept id"
    (let [cmr-host "cmr.sit.earthdata.nasa.gov"
          concept-id "C1200196931-SCIOPS"]
      (is (= (data/cmr-link cmr-host concept-id)
             "https://cmr.sit.earthdata.nasa.gov/concepts/C1200196931-SCIOPS.html")))))
