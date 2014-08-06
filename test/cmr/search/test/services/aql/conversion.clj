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
    (are [cond-args aql-snippet]
         (= (apply q/string-condition :provider-id cond-args)
            (aql-string-elem->condition aql-snippet))

         ;; string value
         ["PROV1"] "<value>PROV1</value>"

         ;; string value with caseInsensitive attribute
         ["PROV1"] "<value caseInsensitive=\"Y\">PROV1</value>"
         ["PROV1"] "<value caseInsensitive=\"y\">PROV1</value>"
         ["PROV1" true false] "<value caseInsensitive=\"N\">PROV1</value>"
         ["PROV1" true false] "<value caseInsensitive=\"n\">PROV1</value>"

         ;; textPattern
         ["PROV*" false true] "<textPattern>PROV*</textPattern>"
         ["P*" false true] "<textPattern>P%</textPattern>"
         ["*1" false true] "<textPattern>%1</textPattern>"
         ["?1" false true] "<textPattern>_1</textPattern>"
         ["PROV?" false true] "<textPattern>PROV_</textPattern>"
         ["P\\%\\_R*V?" false true] "<textPattern>P\\%\\_R%V_</textPattern>"

         ;; textPattern with caseInsensitive attribute
         ["PROV?" false true] "<textPattern caseInsensitive=\"Y\">PROV?</textPattern>"
         ["PROV?" false true] "<textPattern caseInsensitive=\"y\">PROV?</textPattern>"
         ["PROV?" true true] "<textPattern caseInsensitive=\"N\">PROV?</textPattern>"
         ["PROV?" true true] "<textPattern caseInsensitive=\"n\">PROV?</textPattern>")

    (are [condition aql-snippet]
         (= condition
            (aql-string-elem->condition aql-snippet))

         ;; list
         (q/string-condition :provider-id "PROV1") "<list><value>PROV1</value></list>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2")])
         "<list><value>PROV1</value><value>PROV2</value></list>"

         ;; list with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false)
         "<list><value caseInsensitive=\"N\">PROV1</value></list>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                     (q/string-condition :provider-id "PROV2" false false)])
         "<list><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></list>"

         ;; patternList
         (q/string-condition :provider-id "PROV1") "<patternList><value>PROV1</value></patternList>"
         (q/string-condition :provider-id "PROV*" false true)
         "<patternList><textPattern>PROV%</textPattern></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV2")])
         "<patternList><value>PROV1</value><value>PROV2</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1")
                     (q/string-condition :provider-id "PROV?" false true)])
         "<patternList><value>PROV1</value><textPattern>PROV_</textPattern></patternList>"

         ;; patternList with caseInsensitive attribute
         (q/string-condition :provider-id "PROV1" true false)
         "<patternList><value caseInsensitive=\"N\">PROV1</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                     (q/string-condition :provider-id "PROV2" false false)])
         "<patternList><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></patternList>"

         (q/or-conds[(q/string-condition :provider-id "PROV1" true true)
                     (q/string-condition :provider-id "PROV2" false false)])
         "<patternList><textPattern caseInsensitive=\"N\">PROV1</textPattern><value caseInsensitive=\"y\">PROV2</value></patternList>")))


