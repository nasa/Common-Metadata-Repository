(ns cmr.ingest.api.core-test
  "tests functions in core"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.ingest.api.core :as core]))

(defn- string->stream

  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(deftest read-body-test
  (testing "Test the read-body! function to make sure it can handle all the original and new cases"
  (are3 [data expected]
        (is (= expected (core/read-multiple-body! (string->stream data))))

        "Empty strings"
        "" [""]

        "Some simple text, passes thru"
        "something" ["something"]

        "Looks like JSON, passed thru"
        "{\"name\": \"value\"}" ["{\"name\": \"value\"}"]

        "JSON with a URL in it, passes thru"
        "{\"url\": \"http://fake.gov/path?content=value&data=value\"}"
        ["{\"url\": \"http://fake.gov/path?content=value&data=value\"}"]

        "looks like a payload, but it is not JSON, passes thru"
        "content=something&data=else" ["content=something&data=else"]

        "expected payload example for JSON only, split up"
        "content={\"url\":\"http://fake.gov/path?content=value&data=value\"}&data={\"XYZ\": \"zyx\"}"
        ["{\"url\":\"http://fake.gov/path?content=value&data=value\"}"
         "{\"XYZ\": \"zyx\"}"])))
