(ns cmr.search.site.data-test
  (:require
   [clojure.test :refer :all]
   [cmr.search.site.data :as data]))

(deftest app-url->virtual-directory-url-test
  (are [input expected]
      (= expected
         (data/app-url->virtual-directory-url input))

    ;; PROD
    "https://cmr.earthdata.nasa.gov/search/"
    "https://cmr.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.earthdata.nasa.gov/search"
    "https://cmr.earthdata.nasa.gov/virtual-directory/"

    ;; SIT
    "https://cmr.sit.earthdata.nasa.gov/search"
    "https://cmr.sit.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.sit.earthdata.nasa.gov/search"
    "https://cmr.sit.earthdata.nasa.gov/virtual-directory/"

    ;; UAT
    "https://cmr.uat.earthdata.nasa.gov/search"
    "https://cmr.uat.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.uat.earthdata.nasa.gov/search"
    "https://cmr.uat.earthdata.nasa.gov/virtual-directory/"

    ;; localhost doesn't matter
    "http://localhost:3003"
    "http://localhost:3003"))
