(ns cmr.ingest.api.core-test
  "tests functions in core"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.ingest.api.core :as core]))

(defn- string->stream
  "Turn a string into a stream"
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(deftest read-body-test
  (testing "Test the read-body! function to make sure it can handle all the original and new cases"
    (are3 [expected given]
          (is (= expected (core/read-multiple-body! (string->stream given)))
              (str "Failed on [" given "]."))

          "Empty strings"
          [""]
          ""

          "Some simple text, passes thru"
          ["{}"]
          "{}"

          "Looks like JSON, passed thru"
          ["{\"name\": \"value\"}"]
          "{\"name\": \"value\"}"

          "JSON with a URL in it, passes thru"
          ["{\"url\": \"http://fake.gov/path?content=value&data=value\"}"]
          "{\"url\": \"http://fake.gov/path?content=value&data=value\"}"

          "A payload example"
          ["{\"a\":true}" "{\"b\":false}"]
          "{\"content\": {\"a\":true}, \"data\": {\"b\": false}}"

          "expected payload example for JSON only, split up"
          ["{\"url\":\"http://fake.gov/path?content=value&data=value\"}" "{\"XYZ\":\"zyx\"}"]
          "{\"content\":{\"url\":\"http://fake.gov/path?content=value&data=value\"},\"data\":{\"XYZ\":\"zyx\"}}"

          "Make sure that XML is passed thru without change"
          ["<example>data</example>"]
          "<example>data</example>")))
