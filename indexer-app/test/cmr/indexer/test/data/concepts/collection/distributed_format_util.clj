(ns cmr.indexer.test.data.concepts.collection.distributed-format-util
  "This namespace conducts unit tests on the distribution-format-util namespace."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.indexer.data.concepts.collection.distributed-format-util :as util]))

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

(deftest remove-text-various-test
  "Test the removal of the txt 'Various: ' both with data that includes 'Various: '
   and data that doesn't."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/remove-text-various test-string)))

    "Remove the word 'Various: '."
    "XML, HTML, DOC"
    "Various: XML, HTML, DOC"

    "Various doesn't exist so don not replace anything."
    "hello, good, orgoodby"
    "hello, good, orgoodby"))

(deftest split-slash-test
  "Test spliting a string or vector of strings by '/' with the excpetion of ar/info
   or arc/info case insensitive."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-slash-input-string test-string)))

    "Split the string by slash '/'."
    ["hello" "good" "goodby"]
    "hello/good/goodby"

    "Do not split by slash '/' if the data includes 'Ar/Info'."
    ["hello Ar/Info goodby"]
    "hello Ar/Info goodby"

    "Do not split by slash '/' if the data includes 'ArC/Info'."
    ["hello ArC/Info goodby"]
    "hello ArC/Info goodby"

    "Do not split by slash '/' if the data includes /2000, /98, /95, case insensitive for the rest /ARC, /INFO, /CF"
    ["This" "is" "windows 98/2000 95/98 97/98 5.0/95 PC/ARC netCDF-3/CF netCDF-4/CF HTML" "XML arc/info Arc/Info test."]
    "This/is/windows 98/2000 95/98 97/98 5.0/95 PC/ARC netCDF-3/CF netCDF-4/CF HTML/XML arc/info Arc/Info test."

    "Check that I get back an empty vector when the input is just a slash"
    []
    "/")

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
    ["hello" "XML/HTML" "goodby"]

    "Do not split by slash '/' if the data includes /2000, /98, /95, case insensitive for the rest /ARC, /INFO, /CF"
    ["This" "is" "windows 98/2000 95/98 97/98 5.0/95 PC/ARC netCDF-3/CF netCDF-4/CF HTML" "XML arc/info Arc/Info test."]
    ["This/is/windows 98/2000 95/98 97/98 5.0/95 PC/ARC netCDF-3/CF netCDF-4/CF HTML/XML arc/info Arc/Info test."]

    "Check that I get back an empty vector when the input is just a slash"
    []
    ["/"]))


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

(deftest split-underscore-or-underscore-test
  "Test spliting a string or vector of strings by '_or_'."

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-underscore-or-underscore-input-string test-string)))

    "Split the string by '_or_'."
    ["hello" "good" "goodby"]
    "hello_or_good_or_goodby"

    "do not split the string by '_or_'."
    "hello, goodor_goodby"
    "hello, goodor_goodby"

    "do not split the string by '_or_'."
    "hello, good-orgoodby"
    "hello, good-orgoodby")

  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-underscore-or-underscore test-string)))

    "Split the string by '_or_'. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml_or_html" "goodby"]))

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
    "hello good goodby"

    "Testing if comma in the front."
    ["hello"]
    ",hello"

    "Testing if comma in the end."
    ["hello"]
    "hello,"

    "Testing if I just have a comma."
    []
    ",")

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
    "hello-good- goodby -ok"

    "do not split the string by '-'."
    ["HDF - EOS"]
    "HDF - EOS"

    "Don't split the string by '-'."
    ["HDF-4, HDF - EOS, HDF-5"]
    "HDF-4, HDF - EOS, HDF-5")


  (are3 [expected-result test-string]
    (is (= expected-result
           (util/parse-distribution-formats-split-by-dash test-string)))

    "Split the string by ' - '. The input is a vector"
    ["hello" "xml" "html" "goodby"]
    ["hello" "xml - html" "goodby"]

    "Don't split the string by '-'."
    ["HDF-4, HDF - EOS, HDF-5"]
    ["HDF-4, HDF - EOS, HDF-5"]))

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

    "Split the string by all types."
    ["hello" "good" "goodby XML" "HTML" "XML1" "HTML2" "XSLX" "OK" "one" "two" "PDF" "RDF"]
    "Various: hello, and good, or goodby XML/HTML, XML1 and HTML2 or XSLX, OK - one;two, PDF_or_RDF"

    "Testing a non parsed string."
    ["XML"]
    "XML"

    "Testing a parsed string with a / prefix."
    ["OK"]
    "/OK"

    "Testing empty string."
    [""]
    ""

    "Testing nil string."
    []
    nil))
