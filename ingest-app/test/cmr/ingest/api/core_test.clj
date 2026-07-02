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

          "Make sure that XML is passed thru with response change"
          ["<example>data</example>"]
          "<example>data</example>")))

(deftest format-and-contextualize-warnings-existing-errors-test
  (testing "single warning and single error get contextualized"
    (let [result {:warnings ["Date should be in the past"]
                  :existing-errors ["bad type"]}
          response (core/format-and-contextualize-warnings-existing-errors
                    result "Warning: " "Error: ")]
      (is (= ["Warning: Date should be in the past"] (:warnings response)))
      (is (= ["Error: bad type"] (:existing-errors response)))))

  (testing "multiple warnings are joined with ';; ' into a single string"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings ["w1" "w2" "w3"] :existing-errors []}
                    "CTX: " "ERR: ")]
      (is (= ["CTX: w1;; w2;; w3"] (:warnings response)))
      (is (= 1 (count (:warnings response))))))

  (testing "multiple errors are joined with ';; ' into a single string"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings nil :existing-errors ["e1" "e2"]}
                    "CTX: " "ERR: ")]
      (is (= ["ERR: e1;; e2"] (:existing-errors response)))))

  (testing "empty warnings and errors become nil"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings [] :existing-errors []}
                    "CTX: " "ERR: ")]
      (is (nil? (:warnings response)))
      (is (nil? (:existing-errors response)))))

  (testing "nil values stay nil"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings nil :existing-errors nil}
                    "CTX: " "ERR: ")]
      (is (nil? (:warnings response)))
      (is (nil? (:existing-errors response)))))

  (testing "missing keys are added as nil (update on absent key)"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {} "CTX: " "ERR: ")]
      (is (contains? response :warnings))
      (is (contains? response :existing-errors))
      (is (nil? (:warnings response)))
      (is (nil? (:existing-errors response)))))

  (testing "other keys in result are preserved untouched"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings ["w"] :existing-errors ["e"] :data {:collection {:Project {:Shortname "Project1"}}} :status :ok}
                    "W: " "E: ")]
      (is (= {:collection {:Project {:Shortname "Project1"}}} (:data response)))
      (is (= :ok (:status response)))))

  (testing "empty context strings still produce joined responseput"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings ["w1" "w2"] :existing-errors ["e1"]}
                    "" "")]
      (is (= ["w1;; w2"] (:warnings response)))
      (is (= ["e1"] (:existing-errors response)))))

  (testing "works with non-vector seqs (e.g. lists, lazy seqs)"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings (list "w1" "w2")
                     :existing-errors (map str ["e1" "e2"])}
                    "W: " "E: ")]
      (is (= ["W: w1;; w2"] (:warnings response)))
      (is (= ["E: e1;; e2"] (:existing-errors response)))))
  
  (testing "single-arity defaults both contexts to nil (no prefix prepended)"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings ["w1" "w2"] :existing-errors ["e1" "e2"]})]
      (is (= ["w1;; w2"] (:warnings response)))
      (is (= ["e1;; e2"] (:existing-errors response)))))

  (testing "two-arity applies warning-context and defaults err-context to nil"
    (let [response (core/format-and-contextualize-warnings-existing-errors
                    {:warnings ["w1" "w2"] :existing-errors ["e1" "e2"]}
                    "W: ")]
      (is (= ["W: w1;; w2"] (:warnings response)))
      (is (= ["e1;; e2"] (:existing-errors response))))))
