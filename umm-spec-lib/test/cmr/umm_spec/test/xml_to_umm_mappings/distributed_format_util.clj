(ns cmr.umm-spec.test.xml-to-umm-mappings.distributed-format-util
  "This namespace conducts unit tests on the distribution-format-util namespace."
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.distributed-format-util :as util]))

(deftest replace-comma-space-and-with-comma-space-test
  "Test the replacement of ', and' with ', ' both with correct and not correct data."

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

  (testing "Split the string by slash '/'."
    (let [test-string "hello/good/goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-slash-input-string test-string)))))

  (testing "Do not split the string by slash '/' if the data includes 'Ar/Info'."
    (let [test-string "hello Ar/Info goodby"]
      (is (= "hello Ar/Info goodby"
             (util/parse-distribution-formats-split-by-slash-input-string test-string)))))

  (testing "Do not split the string by slash '/' if the data includes 'ArC/Info'."
    (let [test-string "hello ArC/Info goodby"]
      (is (= "hello ArC/Info goodby"
             (util/parse-distribution-formats-split-by-slash-input-string test-string)))))

  (testing "Split the string by slash '/'."
    (let [test-string "hello/good/goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-slash test-string)))))

  (testing "Do not split the string by slash '/' if the data includes 'ArC/Info'.
            The input is a vector."
    (let [test-string ["hello" "ArC/Info" "goodby"]]
      (is (= ["hello" "ArC/Info" "goodby"]
             (util/parse-distribution-formats-split-by-slash test-string)))))

  (testing "Split the string by slash '/'. The input data is a vector."
    (let [test-string ["hello" "XML/HTML" "goodby"]]
      (is (= ["hello" "XML" "HTML" "goodby"]
             (util/parse-distribution-formats-split-by-slash test-string))))))

(deftest split-and-test
  "Test spliting a string or vector of strings by ' and ' with the excpetion of .r
   followed by any 2 characters followed by ' and .q' may be followed by other characters."

  (testing "Split the string by ' and '."
    (let [test-string "hello and good and goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-and-input-string test-string)))))

  (testing "do not split the string by ' and '."
    (let [test-string "hello, goodand goodby"]
      (is (= "hello, goodand goodby"
             (util/parse-distribution-formats-split-by-and-input-string test-string)))))

  (testing "do not split the string by ' and '."
    (let [test-string "hello, good andgoodby"]
      (is (= "hello, good andgoodby"
             (util/parse-distribution-formats-split-by-and-input-string test-string)))))

  (testing "Do not split the string by ' and ' because of the exception."
    (let [test-string "hello .rdr and .q10 goodby"]
      (is (= "hello .rdr and .q10 goodby"
             (util/parse-distribution-formats-split-by-and test-string)))))

  (testing "Split the string by ' and '. The input is a vector"
    (let [test-string ["hello" "xml and html" "goodby"]]
      (is (= ["hello" "xml" "html" "goodby"]
             (util/parse-distribution-formats-split-by-and test-string))))))

(deftest split-or-test
  "Test spliting a string or vector of strings by ' or '."

  (testing "Split the string by ' or '."
    (let [test-string "hello or good or goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-or-input-string test-string)))))

  (testing "do not split the string by ' or '."
    (let [test-string "hello, goodor goodby"]
      (is (= "hello, goodor goodby"
             (util/parse-distribution-formats-split-by-or-input-string test-string)))))

  (testing "do not split the string by ' or '."
    (let [test-string "hello, good orgoodby"]
      (is (= "hello, good orgoodby"
             (util/parse-distribution-formats-split-by-or-input-string test-string)))))

  (testing "Split the string by ' or '. The input is a vector"
    (let [test-string ["hello" "xml or html" "goodby"]]
      (is (= ["hello" "xml" "html" "goodby"]
             (util/parse-distribution-formats-split-by-or test-string))))))

(deftest split-comma-test
  "Test spliting a string or vector of strings by ','."

  (testing "Split the string by ','."
    (let [test-string "hello, good,goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-comma-input-string test-string)))))

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

  (testing "Split the string by ' - '."
    (let [test-string "hello - good - goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-dash-input-string test-string)))))

  (testing "do not split the string by '-'."
    (let [test-string "hello-good- goodby -ok"]
      (is (= "hello-good- goodby -ok"
             (util/parse-distribution-formats-split-by-dash-input-string test-string)))))

  (testing "Split the string by ' - '. The input is a vector"
    (let [test-string ["hello" "xml - html" "goodby"]]
      (is (= ["hello" "xml" "html" "goodby"]
             (util/parse-distribution-formats-split-by-dash test-string))))))

(deftest split-semicolon-test
  "Test spliting a string or vector of strings by ';'."

  (testing "Split the string by ';'."
    (let [test-string "hello;good ; goodby"]
      (is (= ["hello" "good" "goodby"]
             (util/parse-distribution-formats-split-by-semicolon-input-string test-string)))))

  (testing "do not split the string by ';'."
    (let [test-string "hello ;amp good ;gt hello;amp ;gtgoodby ok;gt"]
      (is (= ["hello ;amp good ;gt hello;amp ;gtgoodby ok;gt"]
             (util/parse-distribution-formats-split-by-semicolon-input-string test-string)))))

  (testing "Split the string by ';'. The input is a vector"
    (let [test-string ["hello" "xml;html" "goodby"]]
      (is (= ["hello" "xml" "html" "goodby"]
             (util/parse-distribution-formats-split-by-semicolon test-string))))))

(deftest parse-distribution-formats-test
  "Test parsing a string by different split characters of (, and) (, or) (/) (and)
   (or) (,) (-) and (;).  The initial input is a string, the output is either a
   string or a vector of strings."

  (testing "Split the string by all types."
    (let [test-string "hello, and good, or goodby XML/HTML, XML1 and HTML2 or XSLX, OK - one;two"]
      (is (= ["hello" "good" "goodby XML" "HTML" "XML1" "HTML2" "XSLX" "OK" "one" "two"]
             (util/parse-distribution-formats test-string)))))

  (testing "Testing a non parsed string."
    (let [test-string "XML"]
      (is (= ["XML"]
             (util/parse-distribution-formats test-string)))))

  (testing "Testing empty string."
    (let [test-string ""]
      (is (= [""]
             (util/parse-distribution-formats "")))))

  (testing "Testing nil string."
    (let [test-string nil]
      (is (= []
             (util/parse-distribution-formats test-string))))))
