(ns cmr.system-int-test.search.aql-search-test
  "Integration test for AQL specific search issues. General AQL search tests will be included
  in other files by condition."
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))


(deftest aql-validation-test
  (testing "invalid against AQL schema"
    (is (= {:errors [(msg/invalid-aql "Line 1 - cvc-elt.1: Cannot find the declaration of element 'foo'.")]
            :status 422}
           (search/find-refs-with-aql "<foo/>")))
    (is (= {:errors [(msg/invalid-aql "Line 1 - Content is not allowed in prolog.")]
            :status 422}
           (search/find-refs-with-aql "not even valid xml")))
    (is (= {:errors [(msg/invalid-aql (str "Line 7 - cvc-complex-type.2.4.a: Invalid content was "
                                           "found starting with element 'dataSetId'. One of "
                                           "'{granuleCondition, collectionCondition}' is expected."))]
            :status 422}
           (search/find-refs-with-aql
             "<query>
             <for value=\"collections\"/>
             <dataCenterId>
             <all/>
             </dataCenterId>
             <where>
             <dataSetId>
             <value>Dataset2</value>
             </dataSetId>
             </where>
             </query>"))))
  (testing "Valid AQL"
    (is (nil? (:status
                (search/find-refs-with-aql
                  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
                  <!DOCTYPE query SYSTEM \"https://api.echo.nasa.gov/echo/dtd/IIMSAQLQueryLanguage.dtd\">
                  <query>
                  <for value=\"collections\"/>
                  <dataCenterId>
                  <all/>
                  </dataCenterId>
                  <where>
                  <collectionCondition>
                  <dataSetId>
                  <value>Dataset2</value>
                  </dataSetId>
                  </collectionCondition>
                  </where>
                  </query>"))))))

(deftest aql-search-with-multiple-conditions
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :short-name "SHORT"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"
                                                :short-name "Long"}))
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset1"
                                                :short-name "Short"}))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset2"
                                                :short-name "LongOne"}))]
    (index/refresh-elastic-index)

    (testing "multiple conditions with aql"
      (are [items conditions data-center-condition]
           (d/refs-match? items
                          (search/find-refs-with-aql :collection conditions data-center-condition))

           [coll1 coll3] [{:dataSetId "Dataset1"} {:shortName "SHORT"}] {}
           [coll1 coll3] [{:dataSetId "Dataset1"} {:shortName "SHORT" :ignore-case true}] {}
           [coll1] [{:dataSetId "Dataset1"} {:shortName "SHORT" :ignore-case false}] {}
           [] [{:dataSetId "Dataset2"} {:shortName "Long%"}] {}
           [] [{:dataSetId "Dataset2"} {:shortName "Long%" :pattern false}] {}
           [coll2 coll4] [{:dataSetId "Dataset2"} {:shortName "Long%" :pattern true}] {}
           [coll1] [{:dataSetId "Dataset1"} {:shortName "SHORT"}] {:dataCenterId "PROV1"}))

    (testing "multiple collection conditions with aql"
      (are [items aql-snippets]
           (let [conditions (s/join (map #(format "<collectionCondition>%s</collectionCondition>" %)
                                         aql-snippets))
                 aql-string (str "<query><for value=\"collections\"/>"
                                 "<dataCenterId><all/></dataCenterId> <where>"
                                 (format "%s</where></query>" conditions))]
             (d/refs-match? items
                            (search/find-refs-with-aql aql-string)))

           [coll1 coll3] ["<dataSetId><value>Dataset1</value></dataSetId>"
                          "<shortName><value>SHORT</value></shortName>"]
           [coll1 coll3] ["<dataSetId><value>Dataset1</value></dataSetId>"
                          "<shortName><value caseInsensitive=\"Y\">SHORT</value></shortName>"]
           [coll1] ["<dataSetId><value>Dataset1</value></dataSetId>"
                    "<shortName><value caseInsensitive=\"N\">SHORT</value></shortName>"]
           [coll2 coll4] ["<dataSetId><value>Dataset2</value></dataSetId>"
                          "<shortName><textPattern>Long%</textPattern></shortName>"]
           [] ["<dataSetId><value>Dataset1</value></dataSetId>" "<dataSetId><value>Dataset2</value></dataSetId>"]))))
