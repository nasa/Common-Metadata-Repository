(ns cmr.opendap.tests.unit.http.response
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.http.response :as response]
    [xml-in.core :as xml-in]))

(def xml-test-body
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       "<foo foo-attr=\"foo value\">\n"
       "  <bar bar-attr=\"bar value\">\n"
       "    <baz>The baz value1</baz>\n"
       "    <baz>The baz value2</baz>\n"
       "    <baz>The baz value3</baz>\n"
       "   </bar>\n"
       "</foo>\n"))

(def xml-errprs
  (str "<errors>\n"
       "  <error>Hammer time</error>\n"
       "  <error>Can't touch this</error>\n"
       "</errors>\n"))

(deftest parse-xml-body
  (is (= ["The baz value1" "The baz value2" "The baz value3"]
         (xml-in/find-all (response/parse-xml-body xml-test-body)
                          [:foo :bar :baz]))))

(deftest xml-errors
  (is (= ["Hammer time" "Can't touch this"]
         (response/xml-errors xml-errprs))))
