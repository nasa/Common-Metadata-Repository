(ns cmr.system-int-test.ingest.generics-test
  "Tests for the Generic Concept Ingest API"
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [cmr.common.config :as config]
   [cmr.common.generics :as gcfg]
   [cmr.common.util :as cutil]
   [cmr.ingest.api.generic-documents :as gdocs]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config])
  (:import
   [java.util UUID]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture
     {"provguid1" "PROV1"})]))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type provider-id native-id] (generic-request concept-type provider-id native-id nil :get))
  ([concept-type provider-id native-id document method]
  (-> {:method method
       :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
       :connection-manager (system/conn-mgr)
       :body (when document (json/generate-string document))
       :throw-exceptions false
       :headers {"Accept" "application/json"
                 transmit-config/token-header
                 (transmit-config/echo-system-token)}}
      (clj-http.client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest validate-json-test
  "Check the functions which validate generic metadata"

  (testing
   "Test that approved-generic? approves configured Generic document types"
    (with-redefs [config/approved-pipeline-documents (fn [] {:grids ["1.0.0"]})]
      (is (true? (gcfg/approved-generic? :grids "1.0.0")) "Grids should be an approved format")
      (is (not (gcfg/approved-generic? :grid "0.0.1")) "CMR is using default configuration")
      (is (not (gcfg/approved-generic? :fake "a.b.c")) "A fake type was incorectly approved")))

  (testing
   "Verify that a document can be validated as approved schema."
    (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
      (let [expected nil
            actual (gdocs/validate-json-against-schema
                    :grid
                    "0.0.1"
                    (json/generate-string gen-util/grid-good))]
        (is (= expected actual) "Grid 0.0.1 could not be found"))

      (let [bad-json (-> gen-util/grid-good
                         (dissoc :MetadataSpecification) ;; remove a required tag
                         (json/generate-string))]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"While validating the record against the \[:grid\] schema with version \[0.0.1\] the following error occurred: \[#: required key \[MetadataSpecification\] not found\]. The record cannot be ingested."
             (gdocs/validate-document-against-schema :grid "0.0.1" bad-json))
            "Was not able to generate a schema exception")))))

(deftest test-validate-concept-subtypes
  "Test that concept prefixes can be looked up from either a configuration file or be assumed"

  (testing
   "Test that concept prefixes can be looked up from either a configuration file or be assumed"
    (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
      (let [expected1 "GRD"
            actual1 (gdocs/get-sub-concept-type-concept-id-prefix :grid "0.0.1")
            expected2 "X"
            actual2 (gdocs/get-sub-concept-type-concept-id-prefix :fake "A.B.C")]
        (is (= expected1 actual1) "was not able to find GRD")
        (is (= expected2 actual2) "was not able to default to X")))))

(deftest test-generic-CRUD
  "Test that a Generic can be walked through all the CRUD actions using the ingest
   interface. Use the same native-id for all these steps"

  (let [native-id (format "Generic-Test-%s" (UUID/randomUUID))
        generic-requester (partial generic-request "grid" "PROV1" native-id)
        good-generic-requester (partial generic-requester gen-util/grid-good)]

    (testing "send a good document with config set that does not include grid"
      (with-redefs [config/approved-pipeline-documents (fn [] {:fake ["u.r.a.b"]})]
        (let [result (good-generic-requester :post)]
          (is (= {:fake ["u.r.a.b"]} (config/approved-pipeline-documents)) "failed sanity check")
          (is (= 422 (:status result)) "The HTTP status code is not correct")
          (is (= "application/json" (get-in result [:headers "Content-Type"]))
              "The content type is not correct")
          (is (= "{\"errors\":[\"The [:grid] schema on version [0.0.1] is not an approved schema. This record cannot be ingested.\"]}"
                 (:body result))
              "The body is not correct"))))

    (testing "CREATE a document with a valid config set that includes grid"
      (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
        (let [expected {:Info {:native-id native-id
                               :document-name "Grid-A7-v1"
                               :provider-id "PROV1"
                               :schema "grid"
                               :format "application/vnd.nasa.cmr.umm+json;version=0.0.1"
                               :mime-type "application/grid;version=0.0.1"
                               :revision-id 1
                               :user-id "ECHO_SYS"}
                        :Metadata gen-util/grid-good}

              create-result (good-generic-requester :post)
              create-response (:body create-result)

              read-result (generic-requester)
              read-response (:body read-result)

              actual (json/parse-string read-response true)
              concept-id (get-in actual [:Info :concept-id])
              normalised (-> actual
                           ;; these fields are complicated to test, do so another way
                             (cutil/dissoc-in [:Info :concept-id])
                             (cutil/dissoc-in [:Info :revision-date])
                             (cutil/dissoc-in [:Info :created-at]))
              expected-pattern (re-pattern "\\{\"concept-id\":\"GRD[0-9]+-PROV1\",\"revision-id\":1\\}")]

          ;; test that the create was a success
          (is (= 201 (:status create-result)) "The HTTP status code from create is not correct")
          (is (some? (re-matches expected-pattern create-response))
              "Create returned content when it should not have")

          ;; test that the created document can be read back and was stored correctly
          (is (= 200 (:status read-result)) "The HTTP status code from read is not correct")
          (is (= "application/json;charset=utf-8" (get-in read-result [:headers "Content-Type"]))
              "The content type is not correct)")

          ;; test the three complicated fields
          (is (some? (re-matches #"GRD[0-9]+-PROV1" concept-id)) "A concept id was not returned")
          (is (some? (get-in actual [:Info :revision-date])) "Response did not have a revision date")
          (is (some? (get-in actual [:Info :created-at])) "Response did not have a create date")

          ;; Test the content in sections to make it easy to see where a problem may be
          (is (= (:Info expected) (:Info normalised)) "Info section does not match")
          (is (= (:Metadata expected) (:Metadata normalised)) "Metadata section did not match"))))

    (comment "READ was tested above and will be tested below, no need to do it here")

    (testing "UPDATE The document from above"
      (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
        ;; Update is based on insert and read, so we don't need to test everything like above
        (let [expected "This has been updated"
              input (update-in gen-util/grid-good [:Description] (fn [_] expected))
              update-result (generic-requester input :put)
              update-body (:body update-result)

              read-result (generic-requester)
              read-body (:body read-result)
              read-json (json/parse-string read-body true)
              actual (get-in read-json [:Metadata :Description])
              expected-pattern (re-pattern "\\{\"concept-id\":\"GRD[0-9]+-PROV1\",\"revision-id\":2\\}")]

          ;; Test that the update was successfull
          (is (= 200 (:status update-result))
              "The HTTP status code was returned correctly from the update")
          (is (some? (re-matches expected-pattern update-body))
              "The update body was not empty")

          ;; Test that the read saved document was updated
          (is (= 200 (:status read-result))
              "The HTTP status code not returned correctly from the read")
          (is (= expected actual) "The description was not updated."))))

    ;; TODO: Generic work: add delete
    (testing "DELETE The document from above, twice."
      (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
        (let [result1 (good-generic-requester :delete)
              result2 (good-generic-requester :delete)
              expected-pattern1 (re-pattern "\\{\"concept-id\":\"GRD[0-9].*-PROV1\",\"revision-id\":3.*?")
              expected-pattern2 (re-pattern "\\{\"errors\":\\[\"Concept with native-id \\[Generic-Test-[0-9a-z-].* and concept-id \\[GRD[0-9].*-PROV1\\] is already deleted.\"\\]\\}")]

          (is (= 200 (:status result1)) "First delete was not successfull")
          (is (some? (re-matches expected-pattern1 (:body result1)))
              "Response from first delete was wrong")

          (is (= 404 (:status result2)) "Second delete did not fail correctly")
          (is (some? (re-matches expected-pattern2 (:body result2)))
              "Response from second delete was wrong"))))))
