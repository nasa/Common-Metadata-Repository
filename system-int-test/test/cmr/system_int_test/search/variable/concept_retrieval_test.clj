(ns cmr.system-int-test.search.variable.concept-retrieval-test
  "Integration test for variable retrieval via the following endpoints:

  * /concepts/:concept-id
  * /concepts/:concept-id/:revision-id"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.variable-util :as variable]
   [cmr.transmit.config :as transmit-config]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1"
                          "provguid2" "PROV2"
                          "provguid3" "PROV3"})
   variable/grant-all-variable-fixture]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Retrieve by concept-id - general test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest retrieve-variable-by-concept-id-any-accept-header
  (let [var1-name "variable1"
        var1-v1 (variable/ingest-variable-with-attrs {:Name var1-name})
        del-var (variable/ingest-variable-with-attrs {:Name "deleted-variable"})
        _ (index/wait-until-indexed)
        del-concept (mdb/get-concept (:concept-id del-var))
        ;; tokens
        guest-token (e/login-guest (s/context))
        user1-token (e/login (s/context) "user1")]
    (ingest/delete-concept del-concept (variable/token-opts user1-token))
    (index/wait-until-indexed)
    (testing "retrieval of a deleted variable results in a 404"
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id del-var)
                                      nil
                                      {:accept mt/any
                                       :throw-exceptions true
                                       :headers {transmit-config/token-header user1-token}}))]
        (is (= 404 status))
        (is (= [(format "Concept with concept-id [%s] could not be found."
                        (:concept-id del-var))]
               errors))))
    (let [var1-name-new "variable1-new"
          var1-v2 (variable/ingest-variable-with-attrs
                   {:Name var1-name-new
                    :native-id (:native-id var1-v1)})]
      (testing (str "Sanity check that the test variable got updated and its "
                    "revision id was incremented.")
        (is (= 2
               (inc (:revision-id var1-v1))
               (:revision-id var1-v2))))
      (let [response (search/retrieve-concept
                     (:concept-id var1-v1)
                     nil
                     {:accept mt/any
                      :headers {transmit-config/token-header user1-token}})
            response-v1 (search/retrieve-concept
                         (:concept-id var1-v1)
                         1
                         {:accept mt/any
                          :headers {transmit-config/token-header user1-token}})
            response-v2 (search/retrieve-concept
                         (:concept-id var1-v1)
                         2
                         {:accept mt/any
                          :headers {transmit-config/token-header user1-token}})]
          (testing "retrieval by variable concept-id returns the latest revision."
            (is (= var1-name-new
                   (:Name (json/parse-string (:body response) true)))))
          (testing (str "retrieval by variable concept-id and revision-id returns "
                    "the specified variable")
            (is (= var1-name
                   (:Name (json/parse-string (:body response-v1) true))))
            (is (= var1-name-new
                   (:Name (json/parse-string (:body response-v2) true)))))))
    (testing "retrieval by variable concept-id and incorrect revision-id returns error"
      (let [no-rev 10000
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id var1-v1)
                                      no-rev
                                      {:accept mt/any
                                       :throw-exceptions true
                                       :headers {transmit-config/token-header user1-token}}))]
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
                                       :throw-exceptions true
                                       :headers {transmit-config/token-header user1-token}}))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        no-var
                        no-rev)]
               errors))))))
