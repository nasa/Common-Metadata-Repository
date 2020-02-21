(ns cmr.umm-spec.test.xml-to-umm-mappings.distributed-format-util
  "This namespace conducts unit tests on the distribution-format-util namespace."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.umm-spec.xml-to-umm-mappings.distributed-format-util :as util]))

(deftest replace-comma-space-and-with-comma-space-test
  "Test the replacement of ', and' with ', ' both with correct and not correct data."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-replace-comma-and-with-comma test-string)))

    "Replace ', and ' (comma space and) with just ', ' (comma space)."
    "hello, good, goodby"
    "hello, good, and goodby"

    "Do not replace ', and ' (comma space and) with just ', ' (comma space)."
    "hello, good, andgoodby"
    "hello, good, andgoodby"

    "Do not replace ', and ' (comma space and) with just ', ' (comma space).
     The input string is a string and the output is with the ', and ' replaced
     with ', '."
     "hello, good,and goodby"
     "hello, good,and goodby"))

(deftest replace-comma-space-or-with-comma-space-test
  "Test the replacement of ', or' with ', ' both with correct and not correct data."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-replace-comma-or-with-comma test-string)))

    "Replace ', or ' (comma space or) with just ', ' (comma space)."
    "hello, good, goodby"
    "hello, good, or goodby"

    "Do not replace ', or ' (comma space or) with just ', ' (comma space)."
    "hello, good, orgoodby"
    "hello, good, orgoodby"

    "Do not replace ', or ' (comma space and) with just ', ' (comma space)."
    "hello, good,or goodby"
    "hello, good,or goodby"))

(deftest split-slash-test
  "Test spliting a string or vector of strings by '/' with the excpetion of ar/info
   or arc/info case insensitive."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-slash-input-string test-string)))

    "Split the string by slash '/'."
    ["hello" "good" "goodby"]
    "hello/good/goodby"

    "Do not split the string by slash '/' if the data includes 'Ar/Info'."
    "hello Ar/Info goodby"
    "hello Ar/Info goodby"

    "Do not split the string by slash '/' if the data includes 'ArC/Info'."
    "hello ArC/Info goodby"
    "hello ArC/Info goodby")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-slash test-string)))

    "Split the string by slash '/'."
    ["hello" "good" "goodby"]
    "hello/good/goodby"

    "Do not split the string by slash '/' if the data includes 'ArC/Info'.
    The input is a vector."
    ["hello" "ArC/Info" "goodby"]
    ["hello" "ArC/Info" "goodby"]

    "Split the string by slash '/'. The input data is a vector."
    ["hello" "XML" "HTML" "goodby"]
    ["hello" "XML/HTML" "goodby"]))

(deftest split-and-test
  "Test spliting a string or vector of strings by ' and ' with the excpetion of .r
   followed by any 2 characters followed by ' and .q' may be followed by other characters."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-and-input-string test-string)))

    "Split the string by ' and '."
    ["hello" "good" "goodby"]
    "hello and good and goodby"

    "do not split the string by ' and '."
    "hello, goodand goodby"
    "hello, goodand goodby"

    "do not split the string by ' and '."
    "hello, good andgoodby"
    "hello, good andgoodby")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-and test-string)))

    "Do not split the string by ' and ' because of the exception."
    "hello .rdr and .q10 goodby"
    "hello .rdr and .q10 goodby"

    "Split the string by ' and '. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml and html" "goodby"]))

(deftest split-or-test
  "Test spliting a string or vector of strings by ' or '."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-or-input-string test-string)))

    "Split the string by ' or '."
    ["hello" "good" "goodby"]
    "hello or good or goodby"

    "do not split the string by ' or '."
    "hello, goodor goodby"
    "hello, goodor goodby"

    "do not split the string by ' or '."
    "hello, good orgoodby"
    "hello, good orgoodby")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-or test-string)))

    "Split the string by ' or '. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml or html" "goodby"]))

(deftest split-comma-test
  "Test spliting a string or vector of strings by ','."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-comma-input-string test-string)))

    "Split the string by ','."
    ["hello" "good" "goodby"]
    "hello, good,goodby"

    "do not split the string by ', '."
    "hello good goodby"
    "hello good goodby")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-comma test-string)))

    "Split the string by ','. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml, html" "goodby"]))

(deftest split-dash-test
  "Test spliting a string or vector of strings by ' - '."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-dash-input-string test-string)))

    "Split the string by ' - '."
    ["hello" "good" "goodby"]
    "hello - good - goodby"

    "do not split the string by '-'."
    "hello-good- goodby -ok"
    "hello-good- goodby -ok")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-dash test-string)))

    "Split the string by ' - '. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml - html" "goodby"]))

(deftest split-semicolon-test
  "Test spliting a string or vector of strings by ';'."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-semicolon-input-string test-string)))

    "Split the string by ';'."
    ["hello" "good" "goodby"]
    "hello;good ; goodby"

    "do not split the string by ';'. The output is a vector"
    ["hello ;amp good ;gt hello;amp ;gtgoodby ok;gt"]
    "hello ;amp good ;gt hello;amp ;gtgoodby ok;gt")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-semicolon test-string)))

    "Split the string by ';'. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml;html" "goodby"]))

(deftest parse-distribution-formats-test
  "Test parsing a string by different split characters of (, and) (, or) (/) (and)
   (or) (,) (-) and (;).  The initial input is a string, the output is either a
   string or a vector of strings."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats test-string)))

    "Split the string by all types."
    ["hello" "good" "goodby XML" "HTML" "XML1" "HTML2" "XSLX" "OK" "one" "two"]
    "hello, and good, or goodby XML/HTML, XML1 and HTML2 or XSLX, OK - one;two"

    "Testing a non parsed string."
    ["XML"]
    "XML"

    "Testing empty string."
    [""]
    ""

    "Testing nil string."
    []
    nil))
