(ns cmr.search.test.services.aql.conversion
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.models.query :as q]))

(defn- aql-string-elem->condition
  [aql-snippet]
  (let [aql (format "<query><dataCenterId>%s</dataCenterId></query>" aql-snippet)
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection (cx/element-at-path xml-struct [:dataCenterId]))))

(deftest aql-string-conversion-test
  (testing "string aql"
    (are [condition aql-snippet]
         (= condition
            (aql-string-elem->condition aql-snippet))

         ;; string value
         (q/string-condition :provider-id "PROV1") "<value>PROV1</value>"

         ;; string value with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1") "<value caseInsensitive=\"Y\">PROV1</value>"
         (q/string-condition :provider-id "PROV1") "<value caseInsensitive=\"y\">PROV1</value>"
         (q/string-condition :provider-id "PROV1" true false) "<value caseInsensitive=\"N\">PROV1</value>"
         (q/string-condition :provider-id "PROV1" true false) "<value caseInsensitive=\"n\">PROV1</value>"

         ;; textPattern
         (q/string-condition :provider-id "PROV*" false true) "<textPattern>PROV*</textPattern>"
         (q/string-condition :provider-id "P*" false true) "<textPattern>P%</textPattern>"
         (q/string-condition :provider-id "*1" false true) "<textPattern>%1</textPattern>"
         (q/string-condition :provider-id "?1" false true) "<textPattern>_1</textPattern>"
         (q/string-condition :provider-id "PROV?" false true) "<textPattern>PROV_</textPattern>"
         (q/string-condition :provider-id "P\\%\\_R*V?" false true) "<textPattern>P\\%\\_R%V_</textPattern>"

         ;; textPattern with caseInsensitive attribute
         (q/string-condition :provider-id "PROV?" false true) "<textPattern caseInsensitive=\"Y\">PROV?</textPattern>"
         (q/string-condition :provider-id "PROV?" false true) "<textPattern caseInsensitive=\"y\">PROV?</textPattern>"
         (q/string-condition :provider-id "PROV?" true true) "<textPattern caseInsensitive=\"N\">PROV?</textPattern>"
         (q/string-condition :provider-id "PROV?" true true) "<textPattern caseInsensitive=\"n\">PROV?</textPattern>"

         ;; list
         (q/string-condition :provider-id "PROV1") "<list><value>PROV1</value></list>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2")]) "<list><value>PROV1</value><value>PROV2</value></list>"

         ;; list with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false) "<list><value caseInsensitive=\"N\">PROV1</value></list>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                     (q/string-condition :provider-id "PROV2" false false)]) "<list><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></list>"

         ;; patternList
         (q/string-condition :provider-id "PROV1") "<patternList><value>PROV1</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2")]) "<patternList><value>PROV1</value><value>PROV2</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2" false true)]) "<patternList><value>PROV1</value><textPattern>PROV2</textPattern></patternList>"

         ;; patternList with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false) "<patternList><value caseInsensitive=\"N\">PROV1</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                     (q/string-condition :provider-id "PROV2" false false)]) "<patternList><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true true)
                     (q/string-condition :provider-id "PROV2" false false)]) "<patternList><textPattern caseInsensitive=\"N\">PROV1</textPattern><value caseInsensitive=\"y\">PROV2</value></patternList>")))


