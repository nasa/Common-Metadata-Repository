(ns cmr.system-int-test.ingest.generics-test
  "Tests for the Generic Concept Ingest API"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.common.config :as config]
   [cmr.common.generics :as gcfg]
   [cmr.ingest.api.generic-documents :as gdocs]
   [cmr.system-int-test.search.generics-test :as search-generics-test]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(defn grant-all-generic-permission-fixture
  "A test fixture that grants all users the ability to create and modify generic documents."
  [f]
  (echo-util/grant-system-ingest-management (system/context) [:read :update] [:read :update])
  (f))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                                    grant-all-generic-permission-fixture
                                    ;;allow draft ingest permission for guest on PROV1, but not on PROV2.
                                    (gen-util/grant-all-drafts-fixture {"provguid1" "PROV1"}
                                                                       [:read]
                                                                       [:read])]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Check the functions which validate generic metadata

(deftest disabled-for-ingest?
   (testing
    "Test function for disabling ingest of specific concepts"
    (with-redefs [config/generic-ingest-disabled-list (fn [] [:grid])]
     (is (= :grid (gdocs/disabled-for-ingest? :grid)) "Grid is disabled in test and will be returned")
     (is (= nil (gdocs/disabled-for-ingest? :order-option)) "order option is NOT disabled in test and returns nil"))))

(deftest validate-json-test
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
             #"While validating the record against the \[grid\] schema with version \[0.0.1\] the following error occurred: \[#: required key \[MetadataSpecification\] not found\]. The record cannot be ingested."
             (gdocs/validate-document-against-schema :grid "0.0.1" bad-json))
            "Was not able to generate a schema exception"))))
  (testing
   "Verify that if a schema is on the disabled ingest list, it is not ingested and an error message is returned."
    (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]}) config/generic-ingest-disabled-list (fn [] [:grid])]
       (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
             #"The grid schema is currently disabled and cannot be ingested"
            (gdocs/validate-json-against-schema
             :grid
             "0.0.1"
             (json/generate-string gen-util/grid-good)))))))

(deftest test-generic-CRUD
  (let [native-id "Generic-Test-CRUD"
        generic-tokened-request (partial gen-util/generic-request nil "PROV1" native-id)
        generic-requester (partial generic-tokened-request "grid")]

    (testing "send a good document with config set that does not include grid"
      (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
        (let [result (generic-tokened-request "fake-concept-type" gen-util/grid-good :post)]
          (is (= 404 (:status result)) "The HTTP status code is not correct")
          (is (= "text/html" (get-in result [:headers "Content-Type"]))
              "The content type is not correct")
          (is (string/starts-with? (:body result) "<!DOCTYPE html>") "The body is not correct"))))

    (testing "CREATE a document with a valid config set that includes grid"
      (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
        (let [expected {:native-id native-id
                        :deleted false
                        :provider-id "PROV1"
                        :format "application/vnd.nasa.cmr.umm+json;version=0.0.1"
                        :revision-id 1
                        :concept-type "grid"
                        :extra-fields {:document-name "Grid-A7-v1"
                                       :schema "grid"}
                        :metadata gen-util/grid-good}

              create-result (generic-requester gen-util/grid-good :post)
              create-response (:body create-result)

              read-result (generic-requester)
              read-response (:body read-result)

              actual (first (json/parse-string read-response true))
              concept-id (get actual :concept-id)
              ;; these fields are complicated to test, do so another way
              normalised (dissoc actual :concept-id :revision-date :created-at :transaction-id
                                 :user-id)
              expected-pattern (re-pattern "\\{\"concept-id\":\"GRD[0-9]+-PROV1\",\"revision-id\":1,\"warnings\":null,\"existing-errors\":null\\}")]
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
          (is (some? (get actual :revision-date)) "Response did not have a revision date")
          (is (some? (get actual :created-at)) "Response did not have a create date")

          ;; Test the content in sections to make it easy to see where a problem may be
          (is (= (dissoc expected :metadata) (dissoc normalised :metadata)) "Info section does not match")
          (is (= (:metadata expected) (json/parse-string (:metadata actual) true)) "Metadata section did not match"))))

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
              read-json (->> (json/parse-string read-body true)
                             (filter #(= 2 (:revision-id %)))
                             (first))
              metadata (:metadata read-json)
              actual (get (json/parse-string metadata true) :Description)
              expected-pattern (re-pattern "\\{\"concept-id\":\"GRD[0-9]+-PROV1\",\"revision-id\":2,\"warnings\":null,\"existing-errors\":null\\}")]

          ;; Test that the update was successfull
          (is (= 200 (:status update-result))
              "The HTTP status code was returned correctly from the update")
          (is (some? (re-matches expected-pattern update-body))
              "The update body was not empty")

          ;; Test that the read saved document was updated
          (is (= 200 (:status read-result))
              "The HTTP status code not returned correctly from the read")
          (is (= expected actual) "The description was not updated."))))

    (testing "DELETE The document from above, twice."
      (with-redefs [config/approved-pipeline-documents (fn [] {:grid ["0.0.1"]})]
        (let [result1 (generic-requester gen-util/grid-good :delete)
              result2 (generic-requester gen-util/grid-good :delete)
              expected-pattern1 (re-pattern "\\{\"concept-id\":\"GRD[0-9].*-PROV1\",\"revision-id\":3.*?")
              expected-pattern2 (re-pattern "\\{\"errors\":\\[\"Concept with native-id \\[Generic-Test-CRUD.* and concept-id \\[GRD[0-9].*-PROV1\\] is already deleted.\"\\]\\}")]

          (is (= 200 (:status result1)) "First delete was not successfull")
          (is (some? (re-matches expected-pattern1 (:body result1)))
              "Response from first delete was wrong")

          (is (= 404 (:status result2)) "Second delete did not fail correctly")
          (is (some? (re-matches expected-pattern2 (:body result2)))
              "Response from second delete was wrong"))))))

;; Test that native-id is only unique within the same provider and the same concept-type.
(deftest test-native-id-uniqueness
  (let [;;First ingest a Grid concept in PROV1
        native-id "NativeId"
        grid1 (gen-util/ingest-generic-document
               nil "PROV1" native-id :grid gen-util/grid-good :post)
        grid1-concept-id (:concept-id grid1)
        grid1-revision-id (:revision-id grid1)

        ;;Then ingest a Grid concept in PROV2 using the same native-id.
        grid2 (gen-util/ingest-generic-document
              nil "PROV2" native-id :grid gen-util/grid-good :post)
        grid2-concept-id (:concept-id grid2)
        grid2-revision-id (:revision-id grid2)

        ;; Then ingest a data quality summary and a order option concept in PROV2, all using the same native-id
        dqs2 (gen-util/ingest-generic-document
              nil "PROV2" native-id :data-quality-summary gen-util/data-quality-summary :post)
        dqs2-concept-id (:concept-id dqs2)
        dqs2-revision-id (:revision-id dqs2)

        oo2 (gen-util/ingest-generic-document
             nil "PROV2" native-id :order-option gen-util/order-option :post)
        oo2-concept-id (:concept-id oo2)
        oo2-revision-id (:revision-id oo2)]
    ;; Verify that the all the above concepts are distinct concepts with their own unique concept-ids
    ;; and all the above revision-ids are equal to 1.
    (is (and (not= grid2-concept-id grid1-concept-id)
             (not= dqs2-concept-id grid1-concept-id)
             (not= dqs2-concept-id grid2-concept-id)
             (not= oo2-concept-id dqs2-concept-id)
             (not= oo2-concept-id grid1-concept-id)
             (not= oo2-concept-id grid2-concept-id)))
    (is (= 1 grid1-revision-id grid2-revision-id dqs2-revision-id oo2-revision-id))))

;; Test that collection-draft can not be ingested without PROVIDER_CONTEXT acl for the provider.
(deftest test-collection-draft-ingest-permission
  ;; Drafts have permissions to ingest on PROV1, but not on PROV2.
  (let [native-id "NativeId"
        coll-draft (gen-util/ingest-generic-document
                    nil "PROV1" native-id :collection-draft gen-util/collection-draft :post)
        coll-draft-np (gen-util/ingest-generic-document
                       nil "PROV2" native-id :collection-draft gen-util/collection-draft :post)]
    (is (= (:errors coll-draft) nil))
    (is (= (:errors coll-draft-np) ["You do not have PROVIDER_CONTEXT permission to perform that action."]))))

;; Test that collection-draft is removed from database and elastic search when deleted.
(deftest test-collection-draft-removed-from-database-and-elastic-on-delete
  ;; Drafts have permissions to ingest on PROV1, but not on PROV2.
  (let [native-id "NativeId"
        coll-draft (gen-util/ingest-generic-document
                    nil "PROV1" native-id :collection-draft gen-util/collection-draft :post)
        concept-id (:concept-id coll-draft)
        _ (index/wait-until-indexed)
        ;; search for the draft from elastic search should return result
        search-result (search-generics-test/search-request "collection-drafts" (str "concept_id=" concept-id))
        search-status (:status search-result)
        search-body (:body search-result)
        ;; deleting draft should delete from database completely.
        result1 (gen-util/ingest-generic-document
                    nil "PROV1" native-id :collection-draft gen-util/collection-draft :delete)
        ;; Removal from database happens after the index is deleted.
        _ (index/wait-until-indexed)
        result2 (gen-util/ingest-generic-document
                    nil "PROV1" native-id :collection-draft gen-util/collection-draft :delete)]
    ;;Verify that searching for the concept from elastic search does return result.
    (is (= search-status 200))
    (is (string/includes? search-body "<hits>1</hits>"))

    ;;The first delete removes the draft from database completely and returns concept-id.
    ;;The second delete will not say the draft is already deleted because it no longer exists in the database
    (is (= result1 {:concept-id concept-id, :revision-id 2,:warnings nil, :existing-errors nil}))
    (is (= result2 {:errors ["CollectionDraft with native id [NativeId] in provider [PROV1] does not exist."]}))

    ;;Verify that searching for the concept from elastic search won't return anything after the delete.
    (let [result (search-generics-test/search-request "collection-drafts" (str "concept_id=" concept-id))
          status (:status result)
          body (:body result)]
      (is (= status 200))
      (is (string/includes? body "<hits>0</hits>")))))
