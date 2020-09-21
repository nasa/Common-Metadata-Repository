(ns cmr.system-int-test.search.aql-search-test
  "Integration test for AQL specific search issues. General AQL search tests will be included
  in other files by condition."
  (:require
    [clojure.string :as s]
    [clojure.test :refer :all]
    [cmr.search.services.messages.common-messages :as msg]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))


(deftest aql-validation-test
  (testing "invalid against AQL schema"
    (is (= {:errors [(msg/invalid-aql "Exception while parsing invalid XML: Line 1 - cvc-elt.1: Cannot find the declaration of element 'foo'.")]
            :status 400}
           (search/find-refs-with-aql-string "<foo/>")))
    (is (= {:errors [(msg/invalid-aql "Exception while parsing invalid XML: Line 1 - Content is not allowed in prolog.")]
            :status 400}
           (search/find-refs-with-aql-string "not even valid xml")))
    (is (= {:errors [(msg/invalid-aql (str "Exception while parsing invalid XML: Line 7 - cvc-complex-type.2.4.a: Invalid content was "
                                           "found starting with element 'dataSetId'. One of "
                                           "'{granuleCondition, collectionCondition}' is expected."))]
            :status 400}
           (search/find-refs-with-aql-string
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
                (search/find-refs-with-aql-string
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

(deftest aql-pattern-search-test
  (let [make-coll (fn [n entry-title]
                    (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection n {:ShortName entry-title
                                                      :EntryTitle (str "coll" n)})))
        coll1 (make-coll 1 "SHORT")
        coll2 (make-coll 2 "SHO?RT")
        coll3 (make-coll 3 "SHO*RT")
        coll4 (make-coll 4 "SHO%RT")
        coll5 (make-coll 5 "SHO_RT")
        coll6 (make-coll 6 "SHO\\RT")
        coll7 (make-coll 7 "*SHORT")
        coll8 (make-coll 8 "?SHORT")
        coll9 (make-coll 9 "%SHORT")
        coll10 (make-coll 10 "_SHORT")
        coll11 (make-coll 11 "\\SHORT")
        all-colls [coll1 coll2 coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10 coll11]]
    (index/wait-until-indexed)
    (are [search-str items]
         (let [refs (search/find-refs-with-aql :collection [{:shortName search-str :pattern true}])
               result (d/refs-match? items refs)]
           (when-not result
             (println "Expected:" (pr-str (map :entry-title items)))
             (println "Actual:" (pr-str (map :name (:refs refs)))))
           result)
         ;; Exact matches
         "SHORT" [coll1]
         "SHO?RT" [coll2]
         "SHO*RT" [coll3]
         "SHO\\%RT" [coll4]
         "SHO\\_RT" [coll5]
         "SHO\\\\RT" [coll6]
         "*SHORT" [coll7]
         "?SHORT" [coll8]
         "\\%SHORT" [coll9]
         "\\_SHORT" [coll10]
         "\\\\SHORT" [coll11]

         ;; AQL should support quotes around values
         "'SHORT'" [coll1]
         "'SHO?RT'" [coll2]
         "'SHO*RT'" [coll3]

         "''" []
         "" []

         ;; Using patterns
         "%" all-colls
         "SHO_RT" [coll2 coll3 coll4 coll5 coll6]
         "SH%RT" [coll1 coll2 coll3 coll4 coll5 coll6]
         "S%R_" [coll1 coll2 coll3 coll4 coll5 coll6])))

(deftest aql-search-with-query-parameters
  (let [make-coll (fn [n]
                    (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection n {:EntryTitle (str n)})))
        coll1 (make-coll 1)
        coll2 (make-coll 2)
        coll3 (make-coll 3)
        coll4 (make-coll 4)]
    (index/wait-until-indexed)

    (testing "invalid query parameter"
      (is (= {:errors ["Parameter [foo] was not recognized."]
              :status 400}
             (search/find-refs-with-aql :collection [] {} {:query-params {:foo true}}))))

    (testing "without content-type in header is OK"
      (is (= (dissoc (search/find-refs-with-aql :collection []) :took)
             (dissoc (search/find-refs-with-aql-without-content-type :collection []) :took))))

    (testing "valid query parameters"
      (are [params items]
           (let [refs (search/find-refs-with-aql :collection []
                                                 {} {:query-params params})
                 result (d/refs-match? items refs)]
             (when-not result
               (println "Expected:" (pr-str (map :entry-title items)))
               (println "Actual:" (pr-str (map :name (:refs refs)))))
             result)

           {:page-size 1} [coll1]
           {:page-size 1 :page-num 3} [coll3]
           {} [coll1 coll2 coll3 coll4]))))


(deftest aql-search-with-multiple-conditions
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset1"
                                                :ShortName "SHORT"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "Dataset2"
                                                :ShortName "Long"}))
        coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "Dataset1"
                                                :ShortName "Short"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "Dataset2"
                                                :ShortName "LongOne"}))]
    (index/wait-until-indexed)

    (testing "multiple conditions with aql"
      (are [items conditions data-center-condition]
           (d/refs-match? items
                          (search/find-refs-with-aql :collection conditions data-center-condition))

           [coll1] [{:dataSetId "Dataset1"} {:shortName "SHORT"}] {}
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
                            (search/find-refs-with-aql-string aql-string)))

           [coll1] ["<dataSetId><value>Dataset1</value></dataSetId>"
                    "<shortName><value>SHORT</value></shortName>"]
           [coll1 coll3] ["<dataSetId><value>Dataset1</value></dataSetId>"
                          "<shortName><value caseInsensitive=\"Y\">SHORT</value></shortName>"]
           [coll1] ["<dataSetId><value>Dataset1</value></dataSetId>"
                    "<shortName><value caseInsensitive=\"N\">SHORT</value></shortName>"]
           [coll2 coll4] ["<dataSetId><value>Dataset2</value></dataSetId>"
                          "<shortName><textPattern>Long%</textPattern></shortName>"]
           [] ["<dataSetId><value>Dataset1</value></dataSetId>" "<dataSetId><value>Dataset2</value></dataSetId>"]))))
