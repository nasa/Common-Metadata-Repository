(ns cmr.system-int-test.search.variable.concept-retrieval-test
  "Integration test for variable retrieval via the following endpoints:

  * /concepts/:concept-id
  * /concepts/:concept-id/:revision-id"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.collection :as data2-coll]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1"})
   variable/grant-all-variable-fixture]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Retrieve by concept-id - general test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest retrieve-variable-by-concept-id-any-accept-header
  (testing "retrieval of a deleted variable results in a 404"
    (let [coll1 (data2-core/ingest "PROV1" (data2-coll/collection 
                                            {:entry-title "ET1"
                                             :short-name "S1"
                                             :version-id "V1"}))
          _ (index/wait-until-indexed)
          var-concept (variable/make-variable-concept
                       {:Name "var-to-be-deleted"}
                       {:coll-concept-id (:concept-id coll1)})
          del-var (variable/ingest-variable-with-association var-concept)
          del-concept (mdb/get-concept (:concept-id del-var))
          _ (ingest/delete-concept del-concept
                                   (variable/token-opts (e/login (s/context) "user1")))
          _ (index/wait-until-indexed)
          {:keys [status errors]} (search/get-search-failure-xml-data
                                   (search/retrieve-concept
                                    (:concept-id del-var)
                                    nil
                                    {:accept mt/any
                                     :throw-exceptions true}))]
      (is (= 404 status))
      (is (= [(format "Concept with concept-id [%s] could not be found."
                      (:concept-id del-var))]
             errors))))

  (let [coll1 (data2-core/ingest "PROV1" (data2-coll/collection
                                          {:entry-title "ET1"
                                           :short-name "S1"
                                           :version-id "V1"}))
        _ (index/wait-until-indexed)
        var1-name "variable1"
        var1-long-name "variable1-long-name"
        var1-long-name-new "variable1-long-name-new"
        var1-native-id "var1-native-id"
        var1-concept (variable/make-variable-concept
                      {:Name var1-name
                       :LongName var1-long-name}
                      {:native-id var1-native-id
                       :coll-concept-id (:concept-id coll1)})
        var1-v1 (variable/ingest-variable-with-association var1-concept)
        var1-v2 (variable/ingest-variable-with-attrs {:Name var1-name
                                                      :LongName var1-long-name-new
                                                      :native-id var1-native-id})]
    (index/wait-until-indexed)
    (testing (str "Sanity check that the test variable got updated and its "
                  "revision id was incremented.")
      (is (= 2
             (inc (:revision-id var1-v1))
             (:revision-id var1-v2))))
    (let [response (search/retrieve-concept
                    (:concept-id var1-v1)
                    nil
                    {:accept mt/any})
          response-v1 (search/retrieve-concept
                       (:concept-id var1-v1)
                       1
                       {:accept mt/any})
          response-v2 (search/retrieve-concept
                       (:concept-id var1-v1)
                       2
                       {:accept mt/any})]
      (testing "retrieval by variable concept-id returns the latest revision."
        (is (= var1-long-name-new
               (:LongName (json/parse-string (:body response) true)))))
      (testing (str "retrieval by variable concept-id and revision-id returns "
                    "the specified variable")
        (is (= var1-long-name
               (:LongName (json/parse-string (:body response-v1) true))))
        (is (= var1-long-name-new
               (:LongName (json/parse-string (:body response-v2) true))))))

    (testing "retrieval by variable concept-id and incorrect revision-id returns error"
      (let [no-rev 10000
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id var1-v1)
                                      no-rev
                                      {:accept mt/any
                                       :throw-exceptions true}))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        (:concept-id var1-v1)
                        no-rev)]
               errors))))

    (testing "retrieval by non-existent variable and revision returns error"
      (let [no-var "V404404404-PROV1"
            no-rev 10000
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      no-var
                                      no-rev
                                      {:accept mt/any
                                       :throw-exceptions true}))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        no-var
                        no-rev)]
               errors))))))

(deftest retrieve-variable-by-concept-id-umm-json-accept-header
  (let [coll1 (data2-core/ingest "PROV1" (data2-coll/collection
                                          {:entry-title "ET1"
                                           :short-name "S1"
                                           :version-id "V1"}))
        _ (index/wait-until-indexed)
        var1-name "variable1"
        var1-long-name "var1-long-name"
        var1-long-name-new "variable1-new"
        var1-concept (variable/make-variable-concept
                      {:Name var1-name
                       :LongName var1-long-name}
                      {:coll-concept-id (:concept-id coll1)})
        var1-v1 (variable/ingest-variable-with-association var1-concept)
        var1-v2 (variable/ingest-variable-with-attrs {:Name var1-name
                                                      :LongName var1-long-name-new
                                                      :native-id (:native-id var1-v1)})
        _ (index/wait-until-indexed)
        ;; responses
        response (search/retrieve-concept
                  (:concept-id var1-v1)
                  nil
                  {:accept mt/umm-json})
        response-v1 (search/retrieve-concept
                     (:concept-id var1-v1)
                     1
                     {:accept mt/umm-json})
        response-v2 (search/retrieve-concept
                     (:concept-id var1-v1)
                     2
                     {:accept mt/umm-json})]
    (testing "retrieval by variable concept-id returns the latest revision."
      (is (= var1-long-name-new
             (:LongName (json/parse-string (:body response) true)))))
    (testing (str "retrieval by variable concept-id and revision-id returns "
                  "the specified variable")
      (is (= var1-long-name
             (:LongName (json/parse-string (:body response-v1) true))))
      (is (= var1-long-name-new
             (:LongName (json/parse-string (:body response-v2) true)))))
    (testing "retrieval by variable concept-id and incorrect revision-id returns error"
      (let [no-rev 10000
            {:keys [status body]} (search/retrieve-concept
                                   (:concept-id var1-v1)
                                   no-rev
                                   {:accept mt/umm-json})
            errors (:errors (json/parse-string body true))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        (:concept-id var1-v1)
                        no-rev)]
               errors))))
    (testing "unsupported content types return an error"
      (let [unsupported-mt "unsupported/mime-type"
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id var1-v1)
                                      nil
                                      {:accept unsupported-mt
                                       :throw-exceptions true}))]
        (is (= 400 status))
        (is (= [(format (str "The mime types specified in the accept header "
                             "[%s] are not supported.")
                        unsupported-mt)]
               errors))))))
