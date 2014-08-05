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
  (testing "string value"
    (let [converted-condition (aql-string-elem->condition "<value>PROV1</value>")]
      (is (= (q/string-condition :provider-id "PROV1") converted-condition))))
  (testing "string value with caseInsensitive attribute"
    (let [converted-condition (aql-string-elem->condition "<value caseInsensitive=\"Y\">PROV1</value>")]
      (is (= (q/string-condition :provider-id "PROV1") converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<value caseInsensitive=\"y\">PROV1</value>")]
      (is (= (q/string-condition :provider-id "PROV1") converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<value caseInsensitive=\"N\">PROV1</value>")]
      (is (= (q/string-condition :provider-id "PROV1" true false) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<value caseInsensitive=\"n\">PROV1</value>")]
      (is (= (q/string-condition :provider-id "PROV1" true false) converted-condition))))

  (testing "string pattern"
    (let [converted-condition (aql-string-elem->condition "<textPattern>PROV*</textPattern>")]
      (is (= (q/string-condition :provider-id "PROV*" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern>P%</textPattern>")]
      (is (= (q/string-condition :provider-id "P*" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern>%1</textPattern>")]
      (is (= (q/string-condition :provider-id "*1" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern>_1</textPattern>")]
      (is (= (q/string-condition :provider-id "?1" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern>PROV_</textPattern>")]
      (is (= (q/string-condition :provider-id "PROV?" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern>P\\%\\_R%V_</textPattern>")]
      (is (= (q/string-condition :provider-id "P\\%\\_R*V?" false true) converted-condition))))
  (testing "string pattern with caseInsensitive attribute"
    (let [converted-condition (aql-string-elem->condition "<textPattern caseInsensitive=\"Y\">PROV?</textPattern>")]
      (is (= (q/string-condition :provider-id "PROV?" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern caseInsensitive=\"y\">PROV?</textPattern>")]
      (is (= (q/string-condition :provider-id "PROV?" false true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern caseInsensitive=\"N\">PROV?</textPattern>")]
      (is (= (q/string-condition :provider-id "PROV?" true true) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<textPattern caseInsensitive=\"n\">PROV?</textPattern>")]
      (is (= (q/string-condition :provider-id "PROV?" true true) converted-condition))))

  (testing "string list"
    (let [converted-condition (aql-string-elem->condition "<list><value>PROV1</value></list>")]
      (is (= (q/string-condition :provider-id "PROV1") converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<list><value>PROV1</value><value>PROV2</value></list>")]
      (is (= (q/or-conds[(q/string-condition :provider-id "PROV1")
                         (q/string-condition :provider-id "PROV2")])
             converted-condition))))
  (testing "string list with caseInsensitive attribute"
    (let [converted-condition (aql-string-elem->condition "<list><value caseInsensitive=\"N\">PROV1</value></list>")]
      (is (= (q/string-condition :provider-id "PROV1" true false) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<list><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></list>")]
      (is (= (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                         (q/string-condition :provider-id "PROV2" false false)])
             converted-condition))))

  (testing "string patternList"
    (let [converted-condition (aql-string-elem->condition "<patternList><value>PROV1</value></patternList>")]
      (is (= (q/string-condition :provider-id "PROV1") converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<patternList><value>PROV1</value><value>PROV2</value></patternList>")]
      (is (= (q/or-conds[(q/string-condition :provider-id "PROV1")
                         (q/string-condition :provider-id "PROV2")])
             converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<patternList><value>PROV1</value><textPattern>PROV2</textPattern></patternList>")]
      (is (= (q/or-conds[(q/string-condition :provider-id "PROV1")
                         (q/string-condition :provider-id "PROV2" false true)])
             converted-condition))))
  (testing "string patternList with caseInsensitive attribute"
    (let [converted-condition (aql-string-elem->condition "<patternList><value caseInsensitive=\"N\">PROV1</value></patternList>")]
      (is (= (q/string-condition :provider-id "PROV1" true false) converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<patternList><value caseInsensitive=\"N\">PROV1</value><value caseInsensitive=\"y\">PROV2</value></patternList>")]
      (is (= (q/or-conds[(q/string-condition :provider-id "PROV1" true false)
                         (q/string-condition :provider-id "PROV2" false false)])
             converted-condition)))
    (let [converted-condition (aql-string-elem->condition "<patternList><textPattern caseInsensitive=\"N\">PROV1</textPattern><value caseInsensitive=\"y\">PROV2</value></patternList>")]
      (is (= (q/or-conds[(q/string-condition :provider-id "PROV1" true true)
                         (q/string-condition :provider-id "PROV2" false false)])
             converted-condition)))))


