(ns cmr.search.site.data-test
  (:require
   [clojure.test :refer :all]
   [cmr.search.site.data :as data]))

(deftest app-url->virtual-directory-url-test
  (are [input expected]
      (= expected
         (data/app-url->virtual-directory-url input))

    ;; PROD
    "https://cmr.earthdata.nasa.gov/search"
    "https://cmr.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.earthdata.nasa.gov/search/"
    "https://cmr.earthdata.nasa.gov/virtual-directory/"

    ;; SIT
    "https://cmr.sit.earthdata.nasa.gov/search"
    "https://cmr.sit.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.sit.earthdata.nasa.gov/search/"
    "https://cmr.sit.earthdata.nasa.gov/virtual-directory/"

    ;; UAT
    "https://cmr.uat.earthdata.nasa.gov/search"
    "https://cmr.uat.earthdata.nasa.gov/virtual-directory/"

    "https://cmr.uat.earthdata.nasa.gov/search/"
    "https://cmr.uat.earthdata.nasa.gov/virtual-directory/"

    ;; localhost - no change expected
    "http://localhost:3003"
    "http://localhost:3003"))

(deftest app-url->stac-urls-test
  (are [input expected]
      (= expected
         (data/app-url->stac-urls input))

    ;; PROD
    "https://cmr.earthdata.nasa.gov:443/search"
    {:stac-url "https://cmr.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.earthdata.nasa.gov/static-cloudstac"}

    "https://cmr.earthdata.nasa.gov:443/search/"
    {:stac-url "https://cmr.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.earthdata.nasa.gov/static-cloudstac"} 

    ;; SIT
    "https://cmr.sit.earthdata.nasa.gov:443/search"
    {:stac-url "https://cmr.sit.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.sit.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.sit.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.sit.earthdata.nasa.gov/static-cloudstac"} 

    "https://cmr.sit.earthdata.nasa.gov:443/search/"
    {:stac-url "https://cmr.sit.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.sit.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.sit.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.sit.earthdata.nasa.gov/static-cloudstac"}

    ;; UAT
    "https://cmr.uat.earthdata.nasa.gov:443/search"
    {:stac-url "https://cmr.uat.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.uat.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.uat.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.uat.earthdata.nasa.gov/static-cloudstac"}

    "https://cmr.uat.earthdata.nasa.gov:443/search/"
    {:stac-url "https://cmr.uat.earthdata.nasa.gov/stac"
     :cloudstac-url "https://cmr.uat.earthdata.nasa.gov/cloudstac"
     :stac-docs-url "https://cmr.uat.earthdata.nasa.gov/stac/docs/index.html"
     :static-cloudstac-url "https://cmr.uat.earthdata.nasa.gov/static-cloudstac"}

    ;; localhost 
    "http://localhost:3003"
    {:stac-url "http://localhost:3000/stac" 
     :cloudstac-url "http://localhost:3000/cloudstac"
     :stac-docs-url "http://localhost:3000/stac/docs"
     :static-cloudstac-url "http://localhost:3000/static-cloudstac"}))
