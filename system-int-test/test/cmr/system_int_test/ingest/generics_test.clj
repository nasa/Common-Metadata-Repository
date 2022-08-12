(ns cmr.system-int-test.ingest.generics-test
  "Tests for the Generic Concept Ingest API"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.config :as config]
   [cmr.common.generics :as gcfg]
   [cmr.common.util :as cutil]
   [clojure.test :refer :all]
   [cmr.ingest.api.generic-documents :as gdocs]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config])
  (:import
   [java.util UUID]))

;; This is as sample record, a minimalistic Grid to base tests on
(def grid-good
   {:MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/generic/grid/v0.0.1"
                            :Name "Grid"
                            :Version "0.0.1"}
    :Name "Grid-A7-v1"
    :LongName "Grid A-7 version 1.0"
    :Version "v1.0"
    :Description "A sample grid"
    :GridDefinition {:CoordinateReferenceSystemID {:Type "EPSG:4326"
                                                   :URL "https://epsg.io/4326"}
                     :DimensionSize {:Height 3.14
                                     :Width 3.14
                                     :Time "12:00:00Z"
                                     :Other {:Name "Other Dimension Size",
                                             :Value "value here"}}
                     :Resolution {:Unit "km"
                                  :LongitudeResolution 64
                                  :LatitudeResolution 32}
                     :SpatialExtent {:BoundingRectangle {:0_360_DegreeProjection false,
                                                         :NorthBoundingCoordinate -90.0,
                                                         :EastBoundingCoordinate 180.0,
                                                         :SouthBoundingCoordinate 90.0,
                                                         :WestBoundingCoordinate -180.0}}}
    :Organization {:ShortName "nasa.gov"}
    :MetadataDate {:Create "2022-12-31T13:45:45Z"}
    :AdditionalAttribute {:Name "name"
                          :DataType "STRING"
                          :Description "something"}})

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([provider-id native-id] (generic-request provider-id native-id nil :get))
  ([provider-id native-id document method]
  (-> {:method method
       :url (url-helper/ingest-generic-crud-url provider-id native-id)
       :connection-manager (system/conn-mgr)
       :body (when document (json/generate-string document))
       :throw-exceptions false
       :headers {transmit-config/token-header
                 (transmit-config/echo-system-token)}}
      (clj-http.client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest validate-json-test
  "Check the functions which validate generic metadata"

  (testing
   "Test that approved-generic? approves configured Generic document types"
    (let [original-setting (config/approved-pipeline-documents)]
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:grids ["1.0.0"]}))

      (is (true? (gcfg/approved-generic? :grids "1.0.0")) "Grids should be an approved format")
      (is (not (gcfg/approved-generic? :grid "0.0.1")) "CMR is using default configuration")
      (is (not (gcfg/approved-generic? :fake "a.b.c")) "A fake type was incorectly approved")

      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! `original-setting))))

  (testing
   "Verify that a document can be validated as approved schema."
    (let [original-setting (config/approved-pipeline-documents)]
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:grid ["0.0.1"]}))

      (let [expected nil
            actual (gdocs/validate-json-against-schema :grid "0.0.1" (json/generate-string grid-good))]
        (is (= expected actual) "Grid 0.0.1 could not be found"))

      (let [bad-json (-> grid-good
                         (dissoc :MetadataSpecification) ;; remove a required tag
                         (json/generate-string))]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"While validating the record against the \[:grid\] schema with version \[0.0.1\] the following error occurred: \[#: required key \[MetadataSpecification\] not found\]. The record cannot be ingested."
             (gdocs/validate-document-against-schema :grid "0.0.1" bad-json))
            "Was not able to generate a schema exception"))
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! `original-setting)))))

(deftest test-validate-concept-subtypes
  "Test that concept prefixes can be looked up from either a configuration file or be assumed"

  (testing
   "Test that concept prefixes can be looked up from either a configuration file or be assumed"
    (let [original-setting (config/approved-pipeline-documents)]
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:grids ["1.0.0"]}))

      (let [expected1 "GRD"
            actual1 (gdocs/get-sub-concept-type-concept-id-prefix :grid "0.0.1")
            expected2 "X"
            actual2 (gdocs/get-sub-concept-type-concept-id-prefix :fake "A.B.C")]
        (is (= expected1 actual1) "was not able to find GRD")
        (is (= expected2 actual2) "was not able to default to X"))

      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! `original-setting)))))

(deftest test-generic-CRUD
  "Test that a Generic can be walked through all the CRUD actions using the ingest
   interface. Use the same native-id for all these steps"

  (let [original-setting (config/approved-pipeline-documents)
        native-id (format "Generic-Test-%s" (UUID/randomUUID))
        generic-requester (partial generic-request "PROV1" native-id)
        good-generic-requester (partial generic-requester grid-good)]

    (testing "send a good document with config set that does not include grid"
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:fake ["u.r.a.b"]}))
      (let [result (good-generic-requester :post)]
        (is (= 422 (:status result)) "The HTTP status code is not correct")
        (is (= "application/json" (get-in result [:headers "Content-Type"]))
            "The content type is not correct")
        (is (= "{\"errors\":[\"The [:grid] schema on version [0.0.1] is not an approved schema. This record cannot be ingested.\"]}"
               (:body result))
            "The body is not correct")))

    (testing "CREATE a document with a valid config set that includes grid"
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:grid ["0.0.1"]}))
      (let [expected {:Info {:native-id native-id
                             :document-name "Grid-A7-v1"
                             :provider-id "PROV1"
                             :schema "grid"
                             :format "grid"
                             :mime-type "application/grid;version=0.0.1"
                             :revision-id 1
                             :user-id "ECHO_SYS"}
                      :Metadata grid-good}

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
                           (cutil/dissoc-in [:Info :created-at]))]
        ;; test that the create was a success
        (is (= 204 (:status create-result)) "The HTTP status code from create is not correct")
        (is (nil? create-response) "Create returned content when it should not have")

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
        (is (= (:Metadata expected) (:Metadata normalised)) "Metadata section did not match")))

    (comment "READ was tested above and will be tested below, no need to do it here")

    (testing "UPDATE The document from above"
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:grid ["0.0.1"]}))
      ;; Update is based on insert and read, so we don't need to test everything like above
      (let [expected "This has been updated"
            input (update-in grid-good [:Description] (fn [_] expected))
            update-result (generic-requester input :put)
            update-body (:body update-result)

            read-result (generic-requester)
            read-body (:body read-result)
            read-json (json/parse-string read-body true)
            actual (get-in read-json [:Metadata :Description])]
        ;; Test that the update was successfull
        (is (= 204 (:status update-result)) "The HTTP status code was not returned correctly from the update")
        (is (= nil update-body) "The update body was not empty")
        ;; Test that the read saved document was updated
        (is (= 200 (:status read-result)) "The HTTP status code was not returned correctly from the read")
        (is (= expected actual) "The description was not updated.")))

    ;; TODO: Generic work: add delete
    (testing "DELETE The document from above"
      (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! {:grid ["0.0.1"]}))
      (let [result (good-generic-requester :delete)
            body (:body result)]
        (is (= 501 (:status result)))
        (is (= "" body))))
    (dev-sys-util/eval-in-dev-sys `(config/set-approved-pipeline-documents! `original-setting))))
