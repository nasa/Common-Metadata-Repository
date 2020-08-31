(ns cmr.search.site.data-test
  (:require
   [clojure.test :refer :all]
   [cmr.search.site.data :as data]))

(deftest app-url->virtual-directory-url-test
  (testing "with trailing slash"
    (is (= "https://cmr.earthdata.nasa.gov/virtual-directory/"
           (data/app-url->virtual-directory-url "https://cmr.earthdata.nasa.gov/search/"))))
  (testing "without trailing slash"
    (is (= "https://cmr.earthdata.nasa.gov/virtual-directory/"
           (data/app-url->virtual-directory-url "https://cmr.earthdata.nasa.gov/search")))))

