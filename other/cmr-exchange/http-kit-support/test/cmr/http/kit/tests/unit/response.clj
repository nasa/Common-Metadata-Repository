(ns cmr.http.kit.tests.unit.response
  "Note: this namespace is exclusively for unit tests."
  (:require
   [clojure.test :refer :all]
   [cmr.http.kit.response :as response]
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

(def json-test-body
  (str "{\n"
       "     \"body\": {\n"
       "          \"foo\": 25.25,\n"
       "          \"bar\": \"42\"\n"
       "     },\n"
       "     \"headers\": {\n"
       "          \"hits\": 5,\n"
       "          \"token\": \"not-a-token\"\n"
       "     }\n"
       "}\n"))

(def xml-test-errors
  (str "<errors>\n"
       "  <error>Hammer time</error>\n"
       "  <error>Can't touch this</error>\n"
       "</errors>\n"))

(def json-test-errors
  (str "{\n"
       "     \"body\": {\n"
       "          \"errors\": [\"Bearexxxxxxxxsdf is not a valid token\"]\n"
       "     }\n"
       "}"))

(deftest parse-xml-body
  (is (= ["The baz value1" "The baz value2" "The baz value3"]
         (xml-in/find-all (response/parse-xml-body xml-test-body)
                          [:foo :bar :baz]))))

(deftest xml-errors
  (is (= ["Hammer time" "Can't touch this"]
         (response/xml-errors xml-test-errors))))

(deftest parse-json-body
  (is (= 25.25
         (get-in (response/parse-json-result json-test-body) [:body :foo]))))

(deftest json-errors
  (is (= ["Bearexxxxxxxxsdf is not a valid token"]
         (response/json-errors json-test-errors))))
