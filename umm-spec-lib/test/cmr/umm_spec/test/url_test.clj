(ns cmr.umm-spec.test.url-test
  "Tests for cmr.umm-spec.url functions"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.url :as url]))

(deftest format-url
  (are3 [url sanitize? expected]
    (is (= (url/format-url url sanitize?) expected))

    "Space at the end"
    "www.google.com  " true "www.google.com"

    "Spaces in the middle"
    "www .google .com" true "www.google.com"

    "No spaces"
    "www.google.com" true "www.google.com"

    "Do not sanitize"
    "www.google.com  " false "www.google.com  "))
