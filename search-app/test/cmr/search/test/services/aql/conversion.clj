(ns cmr.search.test.services.aql.conversion
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.date-time-parser :as dt-parser]
            [cmr.search.services.aql.conversion :as a]
            [cmr.common-app.services.search.query-model :as q]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.search.services.aql.conversion :as c]
            [cmr.common.test.test-util :as tu]))

(defn- aql-string-elem->condition
  [aql-snippet]
  (let [aql (format "<query><dataCenterId>%s</dataCenterId></query>" aql-snippet)
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection (cx/element-at-path xml-struct [:dataCenterId]))))

(deftest remove-outer-single-quotes-test
  (are [value expected]
       (= expected (c/remove-outer-single-quotes value))
       nil nil
       "" ""
       "'" "'"
       "''" ""
       "'''" "'"
       "abc" "abc"
       "a'b'c" "a'b'c"
       "'a'b'c'" "a'b'c"
       "'a'" "a"
       "'abc" "'abc"
       "abc'" "abc'"))

(deftest aql-pattern-conversion
  (are [aql expected]
       (= expected (c/aql-pattern->cmr-pattern aql))
       "" ""
       "normal" "normal"
       "_b_c_" "?b?c?"
       "%b%c%" "*b*c*"
       "%" "*"

       ;; escaped
       "ab\\_cd" "ab_cd"
       "ab\\%cd" "ab%cd"

       ;; cmr patterns in aql strings
       "*a*b*" "\\*a\\*b\\*"
       "*" "\\*"
       "?a?b?" "\\?a\\?b\\?"
       "?" "\\?"

       ;; Everything combined together
       "*?%\\%_\\_\\\\" "\\*\\?*%?_\\\\"))

(deftest aql-string-conversion-test
  (testing "string aql"
    (are [cond-args aql-snippet]
         (= (apply q/string-condition :provider-id cond-args)
            (aql-string-elem->condition aql-snippet))

         ;; string value
         ["PROV1" true false] "<value>PROV1</value>"

         ;; string value with caseInsensitive attribute
         ["PROV1" false false] "<value caseInsensitive=\"Y\">PROV1</value>"
         ["PROV1" false false] "<value caseInsensitive=\"y\">PROV1</value>"
         ["PROV1" true false] "<value caseInsensitive=\"N\">PROV1</value>"
         ["PROV1" true false] "<value caseInsensitive=\"n\">PROV1</value>"

         ;; textPattern
         ["PROV\\*" true true] "<textPattern>PROV*</textPattern>"
         ["P*" true true] "<textPattern>P%</textPattern>"
         ["*1" true true] "<textPattern>%1</textPattern>"
         ["?1" true true] "<textPattern>_1</textPattern>"
         ["PROV?" true true] "<textPattern>PROV_</textPattern>"
         ["P%_R*V?" true true] "<textPattern>P\\%\\_R%V_</textPattern>"

         ;; textPattern with caseInsensitive attribute
         ["PROV\\?" false true] "<textPattern caseInsensitive=\"Y\">PROV?</textPattern>"
         ["PROV\\?" false true] "<textPattern caseInsensitive=\"y\">PROV?</textPattern>"
         ["PROV\\?" true true] "<textPattern caseInsensitive=\"N\">PROV?</textPattern>"
         ["PROV\\?" true true] "<textPattern caseInsensitive=\"n\">PROV?</textPattern>")

    (are [condition aql-snippet]
         (= condition
            (aql-string-elem->condition aql-snippet))

         ;; list
         (q/string-condition :provider-id "PROV1" true false) "<list><value>PROV1</value></list>"

         (gc/or-conds[(q/string-condition :provider-id "PROV1" true false)
                      (q/string-condition :provider-id "PROV2" true false)])
         "<list><value>PROV1</value><value>PROV2</value></list>"

         ;; list with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false)
         "<list><value caseInsensitive=\"N\">PROV1</value></list>"

         (gc/or-conds[(q/string-condition :provider-id "PROV1" true false)
                      (q/string-condition :provider-id "PROV2" false false)])
         "<list><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></list>"

         ;; patternList
         (q/string-condition :provider-id "PROV1" true false) "<patternList><value>PROV1</value></patternList>"
         (q/string-condition :provider-id "PROV*" true true)
         "<patternList><textPattern>PROV%</textPattern></patternList>"

         (gc/or-conds[(q/string-condition :provider-id "PROV1" true false)
                      (q/string-condition :provider-id "PROV2" true false)])
         "<patternList><value>PROV1</value><value>PROV2</value></patternList>"

         (gc/or-conds[(q/string-condition :provider-id "PROV1" true false)
                      (q/string-condition :provider-id "PROV?" true true)])
         "<patternList><value>PROV1</value><textPattern>PROV_</textPattern></patternList>"

         ;; patternList with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false)
         "<patternList><value caseInsensitive=\"N\">PROV1</value></patternList>"

         (gc/or-conds[(q/string-condition :provider-id "PROV1" true false)
                      (q/string-condition :provider-id "PROV2" false false)])
         "<patternList><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></patternList>"

         (gc/or-conds[(q/string-condition :provider-id "PROV1" true true)
                      (q/string-condition :provider-id "PROV2" false false)])
         "<patternList><textPattern caseInsensitive=\"N\">PROV1</textPattern><value caseInsensitive=\"y\">PROV2</value></patternList>")))


(defn- aql-date-range-elem->condition
  [aql-snippet]
  (let [aql (format "<ECHOLastUpdate><dateRange>%s</dateRange></ECHOLastUpdate>" aql-snippet)
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection xml-struct)))

(deftest aql-date-range-conversion-test
  (testing "date-range aql"
    (are [start-date stop-date aql-snippet]
         (= (q/map->DateRangeCondition
              {:field :updated-since
               :start-date (when start-date (dt-parser/parse-datetime start-date))
               :end-date (when stop-date (dt-parser/parse-datetime stop-date))})
            (aql-date-range-elem->condition aql-snippet))

         "2001-12-03T01:02:03Z" nil
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>"

         ;; Date with missing of optional attributes
         "2001-12-03T00:00:00Z" nil
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\"/> </startDate>"
         "2001-12-03T01:00:00Z" nil
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\"/> </startDate>"
         "2001-12-03T01:02:00Z" nil
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\"/> </startDate>"

         nil "2011-02-12T01:02:03Z"
         "<stopDate> <Date YYYY=\"2011\" MM=\"02\" DD=\"12\" HH=\"01\" MI=\"02\" SS=\"03\"/> </stopDate>"

         "2001-12-03T01:02:03Z" "2011-02-12T01:02:03Z"
         "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>
         <stopDate> <Date YYYY=\"2011\" MM=\"02\" DD=\"12\" HH=\"01\" MI=\"02\" SS=\"03\"/> </stopDate>")))

(defn- aql-boolean-elem->condition
  [aql-snippet]
  (let [xml-struct (x/parse-str aql-snippet)]
    (a/element->condition :collection xml-struct)))

(deftest aql-boolean-conversion-test
  (testing "boolean aql"
    (are [value aql-snippet]
         (= (q/map->BooleanCondition {:field :downloadable :value value})
            (aql-boolean-elem->condition aql-snippet))

         true "<onlineOnly value=\"Y\" />"
         true "<onlineOnly />")))

(deftest aql-datetime-validation-test
  (testing "aql datetime validation"
    (tu/assert-exception-thrown-with-errors
      :bad-request
      ["Invalid datetime in AQL: Value 13 for monthOfYear must be in the range [1,12]"]
      (a/date-time-from-strings "2014" "13" "22" nil nil nil))))

(deftest validate-aql-pattern-test
  (testing "aql pattern string validation success"
    (are [pattern-str]
         (= nil
          (a/validate-aql-pattern pattern-str))

         "some%_*?characters"
         "\\\\Short"
         "\\\\"
         "xyz\\%"
         "\\_"
         "ab\\%\\_\\\\cd\\"))

  (testing "aql pattern string validation failure"
    (are [pattern-str]
         (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Invalid AQL text pattern: "
           (a/validate-aql-pattern pattern-str))

         "aa\\b"
         "\\n"
         "\\*"
         "\\?"
         "aa\\y\\*bb"
         "ab\\%\\_\\\\c\\d\\")))

