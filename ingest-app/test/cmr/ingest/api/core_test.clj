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

(deftest format-and-contextualize-warnings-existing-errors-test
  (testing "single warning and single error get contextualized"
    (let [result {:warnings ["column x not found"]
                  :existing-errors ["bad type"]}
          out (core/format-and-contextualize-warnings-existing-errors
               result "Warning: " "Error: ")]
      (is (= ["Warning: column x not found"] (:warnings out)))
      (is (= ["Error: bad type"] (:existing-errors out)))))

  (testing "multiple warnings are joined with ';; ' into a single string"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings ["w1" "w2" "w3"] :existing-errors []}
               "CTX: " "ERR: ")]
      (is (= ["CTX: w1;; w2;; w3"] (:warnings out)))
      (is (= 1 (count (:warnings out))))))

  (testing "multiple errors are joined with ';; ' into a single string"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings nil :existing-errors ["e1" "e2"]}
               "CTX: " "ERR: ")]
      (is (= ["ERR: e1;; e2"] (:existing-errors out)))))

  (testing "empty collections become nil"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings [] :existing-errors []}
               "CTX: " "ERR: ")]
      (is (nil? (:warnings out)))
      (is (nil? (:existing-errors out)))))

  (testing "nil values stay nil"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings nil :existing-errors nil}
               "CTX: " "ERR: ")]
      (is (nil? (:warnings out)))
      (is (nil? (:existing-errors out)))))

  (testing "missing keys are added as nil (update on absent key)"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {} "CTX: " "ERR: ")]
      (is (contains? out :warnings))
      (is (contains? out :existing-errors))
      (is (nil? (:warnings out)))
      (is (nil? (:existing-errors out)))))

  (testing "other keys in result are preserved untouched"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings ["w"] :existing-errors ["e"] :data {:rows 42} :status :ok}
               "W: " "E: ")]
      (is (= {:rows 42} (:data out)))
      (is (= :ok (:status out)))))

  (testing "empty context strings still produce joined output"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings ["w1" "w2"] :existing-errors ["e1"]}
               "" "")]
      (is (= ["w1;; w2"] (:warnings out)))
      (is (= ["e1"] (:existing-errors out)))))

  (testing "works with non-vector seqs (e.g. lists, lazy seqs)"
    (let [out (core/format-and-contextualize-warnings-existing-errors
               {:warnings (list "w1" "w2")
                :existing-errors (map str ["e1" "e2"])}
               "W: " "E: ")]
      (is (= ["W: w1;; w2"] (:warnings out)))
      (is (= ["E: e1;; e2"] (:existing-errors out))))))
