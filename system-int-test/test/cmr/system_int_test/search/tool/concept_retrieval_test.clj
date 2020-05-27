(ns cmr.system-int-test.search.tool.concept-retrieval-test
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
   [cmr.system-int-test.utils.tool-util :as tool]))

(use-fixtures
 :each
 (join-fixtures
  [(ingest/reset-fixture {"provguid1" "PROV1"})
   tool/grant-all-tool-fixture]))

(defn- assert-retrieved-concept
  "Verify the retrieved concept by checking against the expected tool name,
  which we have deliberately set to be different for each concept revision."
  [concept-id revision-id accept-format expected-tool-name]
  (let [{:keys [status body]} (search/retrieve-concept
                               concept-id revision-id {:accept accept-format})]
    (is (= 200 status))
    (is (= expected-tool-name
           (:Name (json/parse-string body true))))))

(defmulti handle-retrieve-concept-error
  "Execute the retrieve concept call with the given parameters and returns the status and errors
  based on the result format."
  (fn [concept-id revision-id accept-format]
    accept-format))

(defmethod handle-retrieve-concept-error mt/umm-json
  [concept-id revision-id accept-format]
  (let [{:keys [status body]} (search/retrieve-concept concept-id revision-id
                                                       {:accept accept-format})
        errors (:errors (json/parse-string body true))]
    {:status status :errors errors}))

(defmethod handle-retrieve-concept-error :default
  [concept-id revision-id accept-format]
  (search/get-search-failure-xml-data
   (search/retrieve-concept concept-id revision-id {:accept accept-format
                                                    :throw-exceptions true})))

(defn- assert-retrieved-concept-error
  "Verify the expected error code and error message are returned when retrieving the given concept."
  [concept-id revision-id accept-format err-code err-message]
  (let [{:keys [status errors]} (handle-retrieve-concept-error
                                 concept-id revision-id accept-format)]
    (is (= err-code status))
    (is (= [err-message] errors))))

(deftest retrieve-tool-by-concept-id
  ;; We support UMM JSON format; No format and any format are also accepted.
  (doseq [accept-format [mt/umm-json]];; nil mt/any]]
    (let [suffix (if accept-format
                   (mt/mime-type->format accept-format)
                   "nil")
          ;; append result format to tool name to make it unique
          ;; for different formats so that the test can be run for multiple formats
          t1-r1-name (str "t1-r1" suffix)
          t1-r2-name (str "t1-r2" suffix)
          native-id (str "tool1" suffix)
          t1-r1 (tool/ingest-tool-with-attrs {:Name  t1-r1-name
                                              :native-id native-id})
          t1-r2 (tool/ingest-tool-with-attrs {:Name t1-r2-name
                                              :native-id native-id})
          concept-id (:concept-id t1-r1)
          t1-concept (mdb/get-concept concept-id)]
      (index/wait-until-indexed)

      (testing "Sanity check that the test tool got updated and its revision id was incremented"
        (is (= concept-id (:concept-id t1-r2)))
        (is (= 1 (:revision-id t1-r1)))
        (is (= 2 (:revision-id t1-r2))))

      (testing "retrieval by tool concept-id and revision id returns the specified revision"
        (assert-retrieved-concept concept-id 1 accept-format t1-r1-name)
        (assert-retrieved-concept concept-id 2 accept-format t1-r2-name))

      (testing "retrieval by only tool concept-id returns the latest revision"
        (assert-retrieved-concept concept-id nil accept-format t1-r2-name))

      (testing "retrieval by non-existent revision returns error"
        (assert-retrieved-concept-error concept-id 3 accept-format 404
          (format "Concept with concept-id [%s] and revision-id [3] does not exist." concept-id)))

      (testing "retrieval by non-existent concept-id returns error"
        (assert-retrieved-concept-error "TL404404404-PROV1" nil accept-format 404
          "Concept with concept-id [TL404404404-PROV1] could not be found."))

      (testing "retrieval by non-existent concept-id and revision-id returns error"
        (assert-retrieved-concept-error "TL404404404-PROV1" 1 accept-format 404
          "Concept with concept-id [TL404404404-PROV1] and revision-id [1] does not exist."))

      (testing "retrieval of deleted concept"
        ;; delete the service concept
        (ingest/delete-concept t1-concept (tool/token-opts (e/login (s/context) "user1")))
        (index/wait-until-indexed)

        (testing "retrieval of deleted concept without revision id results in error"
          (assert-retrieved-concept-error concept-id nil accept-format 404
            (format "Concept with concept-id [%s] could not be found." concept-id)))

        (testing "retrieval of deleted concept with revision id returns a 400 error"
          (assert-retrieved-concept-error concept-id 3 accept-format 400
            (format (str "The revision [3] of concept [%s] represents "
                         "a deleted concept and does not contain metadata.")
                    concept-id)))))))

(deftest retrieve-tool-with-invalid-format
  (testing "unsupported accept header results in error"
    (let [unsupported-mt "unsupported/mime-type"]
      (assert-retrieved-concept-error "TL1111-PROV1" nil unsupported-mt 400
        (format "The mime types specified in the accept header [%s] are not supported."
                unsupported-mt)))))
