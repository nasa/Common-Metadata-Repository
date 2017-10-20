(ns cmr.system-int-test.search.service.concept-retrieval-test
  "Integration test for service retrieval via the following endpoints:

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
   [cmr.system-int-test.utils.service-util :as service]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1"})
   service/grant-all-service-fixture]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Helper utility functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-svc
  "Ingest a single svc given a svc-name."
  [svc-name]
  (service/ingest-service-with-attrs {:Name svc-name}))

(defn- get-updated-svc
  "Update (re-ingest) an existing (old) service with new data."
  [old-svc data]
  (index/wait-until-indexed)
  (service/ingest-service-with-attrs
   (merge data
          {:native-id (:native-id old-svc)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Retrieve by concept-id - general test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest retrieve-service-by-concept-id-any-accept-header
  (let [svc1-name "service1"
        svc1-name-new "service1-new"
        del-svc-name "deleted-service"
        svc1-v1 (get-svc svc1-name)
        del-svc (get-svc del-svc-name)
        _ (index/wait-until-indexed)
        del-concept (mdb/get-concept (:concept-id del-svc))]
    (ingest/delete-concept del-concept
                           (service/token-opts (e/login (s/context) "user1")))
    (index/wait-until-indexed)
    (testing "retrieval of a deleted service results in a 404"
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id del-svc)
                                      nil
                                      {:accept mt/any
                                       :throw-exceptions true}))]
        (is (= 404 status))
        (is (= [(format "Concept with concept-id [%s] could not be found."
                        (:concept-id del-svc))]
               errors))))
    (let [svc1-v2 (get-updated-svc svc1-v1 {:Name svc1-name-new})]
      (testing (str "Sanity check that the test service got updated and its "
                    "revision id was incremented.")
        (is (= 2
               (inc (:revision-id svc1-v1))
               (:revision-id svc1-v2))))
      (let [response (search/retrieve-concept
                      (:concept-id svc1-v1)
                      nil
                      {:accept mt/any})
            response-v1 (search/retrieve-concept
                         (:concept-id svc1-v1)
                         1
                         {:accept mt/any})
            response-v2 (search/retrieve-concept
                         (:concept-id svc1-v1)
                         2
                         {:accept mt/any})]
          (testing "retrieval by service concept-id returns the latest revision."
            (is (= svc1-name-new
                   (:Name (json/parse-string (:body response) true)))))
          (testing (str "retrieval by service concept-id and revision-id returns "
                    "the specified service")
            (is (= svc1-name
                   (:Name (json/parse-string (:body response-v1) true))))
            (is (= svc1-name-new
                   (:Name (json/parse-string (:body response-v2) true)))))))
    (testing "retrieval by service concept-id and incorrect revision-id returns error"
      (let [no-rev 10000
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id svc1-v1)
                                      no-rev
                                      {:accept mt/any
                                       :throw-exceptions true}))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        (:concept-id svc1-v1)
                        no-rev)]
               errors))))
    (testing "retrieval by non-existent service and revision returns error"
      (let [no-svc "S404404404-PROV1"
            no-rev 10000
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      no-svc
                                      no-rev
                                      {:accept mt/any
                                       :throw-exceptions true}))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        no-svc
                        no-rev)]
               errors))))))

(deftest retrieve-service-by-concept-id-umm-json-accept-header
  (let [svc1-name "service1"
        svc1-name-new "service1-new"
        svc1-v1 (get-svc svc1-name)
        svc1-v2 (get-updated-svc svc1-v1 {:Name svc1-name-new})
        ;; responses
        response (search/retrieve-concept
                  (:concept-id svc1-v1)
                  nil
                  {:accept mt/umm-json})
        response-v1 (search/retrieve-concept
                     (:concept-id svc1-v1)
                     1
                     {:accept mt/umm-json})
        response-v2 (search/retrieve-concept
                     (:concept-id svc1-v1)
                     2
                     {:accept mt/umm-json})]
    (testing "retrieval by service concept-id returns the latest revision."
      (is (= svc1-name-new
             (:Name (json/parse-string (:body response) true)))))
    (testing (str "retrieval by service concept-id and revision-id returns "
              "the specified service")
      (is (= svc1-name
             (:Name (json/parse-string (:body response-v1) true))))
      (is (= svc1-name-new
             (:Name (json/parse-string (:body response-v2) true)))))
    (testing "retrieval by service concept-id and incorrect revision-id returns error"
      (let [no-rev 10000
            {:keys [status body]} (search/retrieve-concept
                                     (:concept-id svc1-v1)
                                     no-rev
                                     {:accept mt/umm-json})
            errors (:errors (json/parse-string body true))]
        (is (= 404 status))
        (is (= [(format (str "Concept with concept-id [%s] and revision-id [%s] "
                             "does not exist.")
                        (:concept-id svc1-v1)
                        no-rev)]
               errors))))
    (testing "unsupported content types return an error"
      (let [unsupported-mt "unsupported/mime-type"
            {:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id svc1-v1)
                                      nil
                                      {:accept unsupported-mt
                                       :throw-exceptions true}))]
        (is (= 400 status))
        (is (= [(format (str "The mime types specified in the accept header "
                             "[%s] are not supported.")
                        unsupported-mt)]
               errors))))))
