(ns cmr.umm-spec.test.xml-to-umm-mappings.distributed-format-util
  "This namespace conducts unit tests on the distribution-format-util namespace."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.umm-spec.xml-to-umm-mappings.distributed-format-util :as util]))

(deftest replace-comma-space-and-with-comma-space-test
  "Test the replacement of ', and' with ', ' both with correct and not correct data."

  ; None of the following test work properly when run through the are3 macro,
  (testing "Replace ', and ' (comma space and) with just ', ' (comma space)."
    (let [test-string "hello, good, and goodby"]
      (is (= "hello, good, goodby"
             (util/parse-distribution-formats-replace-comma-and-with-comma test-string)))))

  (testing "Do not replace ', and ' (comma space and) with just ', ' (comma space)."
    (let [test-string "hello, good, andgoodby"]
      (is (= "hello, good, andgoodby"
             (util/parse-distribution-formats-replace-comma-and-with-comma test-string)))))

  (testing "Do not replace ', and ' (comma space and) with just ', ' (comma space).
            The input string is a string and the output is with the ', and ' replaced
            with ', '."
    (let [test-string "hello, good,and goodby"]
      (is (= "hello, good,and goodby"
             (util/parse-distribution-formats-replace-comma-and-with-comma test-string))))))

(deftest replace-comma-space-or-with-comma-space-test
  "Test the replacement of ', or' with ', ' both with correct and not correct data."

  ; None of the following test work properly when run through the are3 macro,
  (testing "Replace ', or ' (comma space or) with just ', ' (comma space)."
    (let [test-string "hello, good, or goodby"]
      (is (= "hello, good, goodby"
             (util/parse-distribution-formats-replace-comma-or-with-comma test-string)))))

  (testing "Do not replace ', or ' (comma space or) with just ', ' (comma space)."
    (let [test-string "hello, good, orgoodby"]
      (is (= "hello, good, orgoodby"
             (util/parse-distribution-formats-replace-comma-and-with-comma test-string)))))

  (testing "Do not replace ', or ' (comma space and) with just ', ' (comma space)."
    (let [test-string "hello, good,or goodby"]
      (is (= "hello, good,or goodby"
             (util/parse-distribution-formats-replace-comma-and-with-comma test-string))))))

(deftest split-slash-test
  "Test spliting a string or vector of strings by '/' with the excpetion of ar/info
   or arc/info case insensitive."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by slash '/'."
    "hello/good/goodby"
    ["hello" "good" "goodby"]

    "Split the string by slash '/'."
    "hello/good/goodby"
    ["hello" "good" "goodby"]

    "Do not split the string by slash '/' if the data includes 'ArC/Info'.
    The input is a vector."
    ["hello" "ArC/Info" "goodby"]
    ["hello" "ArC/Info" "goodby"]

    "Split the string by slash '/'. The input data is a vector."
    ["hello" "XML/HTML" "goodby"]
    ["hello" "XML" "HTML" "goodby"])

  ; Runing the next two tests do not produce the same output when run through the are3 macro.
  (testing "Do not split the string by slash '/' if the data includes 'Ar/Info'."
    (let [test-string "hello Ar/Info goodby"]
      (is (= "hello Ar/Info goodby"
             (util/parse-distribution-formats-split-by-slash-input-string test-string)))))

  (testing "Do not split the string by slash '/' if the data includes 'ArC/Info'."
    (let [test-string "hello ArC/Info goodby"]
      (is (= "hello ArC/Info goodby"
             (util/parse-distribution-formats-split-by-slash-input-string test-string))))))

(deftest split-and-test
  "Test spliting a string or vector of strings by ' and ' with the excpetion of .r
   followed by any 2 characters followed by ' and .q' may be followed by other characters."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by ' and '."
    "hello and good and goodby"
    ["hello" "good" "goodby"]

    "Split the string by ' and '. The input is a vector"
    ["hello" "xml and html" "goodby"]
    ["hello" "xml" "html" "goodby"])

  (testing "Do not split the string by ' and ' because of the exception."
    (let [test-string "hello .rdr and .q10 goodby"]
      (is (= "hello .rdr and .q10 goodby"
             (util/parse-distribution-formats-split-by-and test-string)))))

  (testing "do not split the string by ' and '."
    (let [test-string "hello, goodand goodby"]
      (is (= "hello, goodand goodby"
             (util/parse-distribution-formats-split-by-and-input-string test-string)))))

  (testing "do not split the string by ' and '."
    (let [test-string "hello, good andgoodby"]
      (is (= "hello, good andgoodby"
             (util/parse-distribution-formats-split-by-and-input-string test-string))))))

(deftest split-or-test
  "Test spliting a string or vector of strings by ' or '."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by ' or '."
    "hello or good or goodby"
    ["hello" "good" "goodby"]

    "Split the string by ' or '. The input is a vector"
    ["hello" "xml or html" "goodby"]
    ["hello" "xml" "html" "goodby"])

  ; These next two tests don't have the same output when run
  ; through the macro. In this case the macro expansion does not
  ; handle the comma correctly.
  (testing "do not split the string by ' or '."
    (let [test-string "hello, goodor goodby"]
      (is (= "hello, goodor goodby"
             (util/parse-distribution-formats-split-by-or-input-string test-string)))))

  (testing "do not split the string by ' or '."
    (let [test-string "hello, good orgoodby"]
      (is (= "hello, good orgoodby"
             (util/parse-distribution-formats-split-by-or-input-string test-string))))))

(deftest split-comma-test
  "Test spliting a string or vector of strings by ','."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by ','."
    "hello, good,goodby"
    ["hello" "good" "goodby"])

  ; These next two tests don't have the same output when run
  ; through the macro.
  (testing "do not split the string by ', '."
    (let [test-string "hello good goodby"]
      (is (= "hello good goodby"
             (util/parse-distribution-formats-split-by-comma-input-string test-string)))))

  (testing "Split the string by ','. The input is a vector"
    (let [test-string ["hello" "xml, html" "goodby"]]
      (is (= ["hello" "xml" "html" "goodby"]
             (util/parse-distribution-formats-split-by-comma test-string))))))

(deftest split-dash-test
  "Test spliting a string or vector of strings by ' - '."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by ' - '."
    "hello - good - goodby"
    ["hello" "good" "goodby"]

    "Split the string by ' - '. The input is a vector"
    ["hello" "xml - html" "goodby"]
    ["hello" "xml" "html" "goodby"])

  ; This next test doesn't have the same output when run
  ; through the macro.
  (testing "do not split the string by '-'."
    (let [test-string "hello-good- goodby -ok"]
      (is (= "hello-good- goodby -ok"
             (util/parse-distribution-formats-split-by-comma test-string))))))

(deftest split-semicolon-test
  "Test spliting a string or vector of strings by ';'."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by ';'."
    "hello;good ; goodby"
    ["hello" "good" "goodby"]

    "Split the string by ';'. The input is a vector"
    ["hello" "xml;html" "goodby"]
    ["hello" "xml" "html" "goodby"])

  ; This next test doesn't have the same output when run
  ; through the macro.
  (testing "do not split the string by ';'."
    (let [test-string "hello ;amp good ;gt hello;amp ;gtgoodby ok;gt"]
      (is (= "hello ;amp good ;gt hello;amp ;gtgoodby ok;gt"
             (util/parse-distribution-formats-split-by-comma test-string))))))

(deftest parse-distribution-formats-test
  "Test parsing a string by different split characters of (, and) (, or) (/) (and)
   (or) (,) (-) and (;).  The initial input is a string, the output is either a
   string or a vector of strings."

  (are3 [test-string expected-result]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by all types."
    "hello, and good, or goodby XML/HTML, XML1 and HTML2 or XSLX, OK - one;two"
    ["hello" "good" "goodby XML" "HTML" "XML1" "HTML2" "XSLX" "OK" "one" "two"]

    "Testing a non parsed string."
    "XML"
    ["XML"]

    "Testing empty string."
    ""
    [""]

    "Testing nil string."
    nil
    []))
