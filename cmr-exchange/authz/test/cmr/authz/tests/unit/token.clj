(ns cmr.authz.tests.unit.token
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.authz.token :as token]))

(def xml-test-body
  (str "<token_info>\n"
       "  <token>deadbeef-cafe-244837814094590</token>\n"
       "  <user_name>4l1c3</user_name>\n"
       "  <expires type=\"datetime\">2018-06-07T10:54:13Z</expires>\n"
       "  <guest type=\"boolean\">false</guest>\n"
       "  <created type=\"datetime\">2018-05-08T10:54:13Z</created>\n"
       "  <user_guid>abc-123</user_guid>\n"
       "  <client_id>alice@nasa.gov</client_id>\n"
       "</token_info>\n"))

(deftest parse-token
  (is (= ["deadbeef-cafe-244837814094590"]
         (token/parse-token xml-test-body))))

(deftest parse-username
  (is (= "4l1c3"
         (token/parse-username xml-test-body))))
