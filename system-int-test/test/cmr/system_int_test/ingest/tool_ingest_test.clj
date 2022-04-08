(ns cmr.system-int-test.ingest.tool-ingest-test
  "CMR tool ingest integration tests.
  For tool permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.tool-util :as tool-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest tool-ingest-test
  (testing "ingest of a new tool concept"
    (let [concept (tool-util/make-tool-concept)
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))
  (testing "ingest of a tool concept with a revision id"
    (let [concept (tool-util/make-tool-concept {} {:revision-id 5})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (= 5 revision-id))
      (is (mdb/concept-exists-in-mdb? concept-id 5)))))

;; Verify that the accept header works
(deftest tool-ingest-accept-header-response-test
  (let [supplied-concept-id "TL1000-PROV1"]
    (testing "json response"
      (let [response (ingest/ingest-concept
                      (tool-util/make-tool-concept
                       {:concept-id supplied-concept-id})
                      {:accept-format :json
                       :raw? true})]
        (is (= {:revision-id 1
                :concept-id supplied-concept-id}
               (dissoc (ingest/parse-ingest-body :json response) :body)))))

    (testing "xml response"
      (let [response (ingest/ingest-concept
                      (tool-util/make-tool-concept
                       {:concept-id supplied-concept-id})
                      {:accept-format :xml
                       :raw? true})]
        (is (= {:revision-id 2
                :concept-id supplied-concept-id}
               (dissoc (ingest/parse-ingest-body :xml response) :body)))))))

;; Verify that the accept header works with returned errors
(deftest tool-ingest-with-errors-accept-header-test
  (testing "json response"
    (let [concept-no-metadata (assoc (tool-util/make-tool-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)]
      (is (re-find #"Request content is too short." (first errors)))))
  (testing "xml response"
    (let [concept-no-metadata (assoc (tool-util/make-tool-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :xml
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Request content is too short." (first errors))))))

;; Verify that user-id is saved from User-Id or token header
(deftest tool-ingest-user-id-test
  (testing "ingest of new concept"
    (are3 [ingest-headers expected-user-id]
      (let [concept (tool-util/make-tool-concept)
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept ingest-headers)]
        (ingest/assert-user-id concept-id revision-id expected-user-id))

      "user id from token"
      {:token (echo-util/login (system/context) "user1")} "user1"

      "user id from user-id header"
      {:user-id "user2"} "user2"

      "both user-id and token in the header results in the revision getting user id from user-id header"
      {:token (echo-util/login (system/context) "user3")
       :user-id "user4"} "user4"

      "neither user-id nor token in the header"
      {} nil))
  (testing "update of existing concept with new user-id"
    (are3 [ingest-header1 expected-user-id1
           ingest-header2 expected-user-id2
           ingest-header3 expected-user-id3
           ingest-header4 expected-user-id4]
      (let [concept (tool-util/make-tool-concept)
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept ingest-header1)]
        (ingest/ingest-concept concept ingest-header2)
        (ingest/delete-concept concept ingest-header3)
        (ingest/ingest-concept concept ingest-header4)
        (ingest/assert-user-id concept-id revision-id expected-user-id1)
        (ingest/assert-user-id concept-id (inc revision-id) expected-user-id2)
        (ingest/assert-user-id concept-id (inc (inc revision-id)) expected-user-id3)
        (ingest/assert-user-id concept-id (inc (inc (inc revision-id))) expected-user-id4))

      "user id from token"
      {:token (echo-util/login (system/context) "user1")} "user1"
      {:token (echo-util/login (system/context) "user2")} "user2"
      {:token (echo-util/login (system/context) "user3")} "user3"
      {:token nil} nil

      "user id from user-id header"
      {:user-id "user1"} "user1"
      {:user-id "user2"} "user2"
      {:user-id "user3"} "user3"
      {:user-id nil} nil)))

;; Service with concept-id ingest and update scenarios.
(deftest tool-w-concept-id-ingest-test
  (let [supplied-concept-id "TL1000-PROV1"
        metadata {:concept-id supplied-concept-id
                  :native-id "Atlantic-1"}
        concept (tool-util/make-tool-concept metadata)]
    (testing "ingest of a new tool concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))
    (testing "ingest of same native id and different providers is allowed"
      (let [concept2-id "TL1000-PROV2"
            concept2 (tool-util/make-tool-concept
                      (assoc metadata :provider-id "PROV2"
                                      :concept-id concept2-id))
            {:keys [concept-id revision-id]} (ingest/ingest-concept concept2)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [concept2-id 1] [concept-id revision-id]))))

    (testing "update the concept with the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (= [supplied-concept-id 2] [concept-id revision-id]))))

    (testing "update the concept without the concept-id"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept
                                              (dissoc concept :concept-id))]
        (is (= [supplied-concept-id 3] [concept-id revision-id]))))))

(deftest tool-ingest-schema-validation-test
  (testing "ingest of tool concept JSON schema validation missing field"
    (let [concept (tool-util/make-tool-concept {:Type ""})
          {:keys [status errors]} (ingest/ingest-concept concept)]
      (is (= 400 status))
      (is (= ["#/Type:  is not a valid enum value"]
             errors))))
  (testing "ingest of tool concept JSON schema validation invalid field"
    (let [concept (tool-util/make-tool-concept {:InvalidField "xxx"})
          {:keys [status errors]} (ingest/ingest-concept concept)]
      (is (= 400 status))
      (is (= ["#: extraneous key [InvalidField] is not permitted"]
             errors)))))

(deftest tool-update-error-test
  (let [supplied-concept-id "TL1000-PROV1"
        concept (tool-util/make-tool-concept
                 {:concept-id supplied-concept-id
                  :native-id "Atlantic-1"})
        _ (ingest/ingest-concept concept)]
    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :concept-id "TL1111-PROV1"))]
        (is (= [409 [(str "A concept with concept-id [TL1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:tool] provider-id [PROV1]. "
                          "The given concept-id [TL1111-PROV1] and native-id "
                          "[Atlantic-1] would conflict with that one.")]]
               [status errors]))))
    (testing "update concept with a different native-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :native-id "other"))]
        (is (= [409 [(str "A concept with concept-id [TL1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:tool] provider-id [PROV1]. "
                          "The given concept-id [TL1000-PROV1] and native-id "
                          "[other] would conflict with that one.")]]
               [status errors]))))))

(deftest delete-tool-ingest-test
  (testing "delete a tool"
    (let [concept (tool-util/make-tool-concept)
          _ (tool-util/ingest-tool concept)
          {:keys [status concept-id revision-id]}  (ingest/delete-concept concept)
          fetched (mdb/get-concept concept-id revision-id)]
      (is (= 200 status))
      (is (= 2 revision-id))
      (is (= (:native-id concept)
             (:native-id fetched)))
      (is (:deleted fetched))
      (testing "delete a deleted tool"
        (let [{:keys [status errors]} (ingest/delete-concept concept)]
          (is (= [status errors]
                 [404 [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                               (:native-id concept) concept-id)]]))))
      (testing "create a tool over a tool's tombstone"
        (let [response (tool-util/ingest-tool
                        (tool-util/make-tool-concept))
              {:keys [status concept-id revision-id]} response]
          (is (= 200 status))
          (is (= 3 revision-id)))))))

(deftest old-version-tool-ingest-test
  (testing "Ingest of an older version of Tool concept that requires migration"
    (let [{:keys [status]} (d/ingest-concept-with-metadata-file
                            "CMR-7610/umm_t_v_1_0.json"
                            {:concept-type :tool
                             :provider-id "PROV1"
                             :native-id "tool_v_1.0"
                             :format "application/vnd.nasa.cmr.umm+json; version=1.0"})]
      (is (= 201 status)))))

(deftest related-url-content-type-type-and-subtype-check
  (testing "Check valid and invalid URL and RelatedURLs at ingest."
    ;;Check valid and invalid related urls and url's url content type, type, and subtype.
    ;;The first RelatedURL is good while the second and the third are bad.
    ;;The first URL is bad while the second one is good. The error response should
    ;;represent this showing the three bad ones failed to match a valid keyword.
    (let [related-urls [{"URL" "https://example.gov/good1"
                         "URLContentType" "PublicationURL"
                         "Type" "VIEW RELATED INFORMATION"
                         "Subtype" "HOW-TO"}  ;this is the valid Related URL
                        {"URL" "https://example.gov/bad1"
                         "URLContentType" "PublicationURL"
                         "Type" "USE SERVICE APIs"
                         "Subtype" "Closed Search"}
                        {"URL" "https://example.gov/bad2"
                         "URLContentType" "PublicationURL"
                         "Type" "USE SERVICE APIs"
                         "Subtype" "Very Closed Search"}]
          url1 {"URLContentType" "PublicationURL"
               "Type" "invalid"
               "URLValue" "https://lpdaacsvc.cr.usgs.gov/appeears/bad3"}
          url2 {"URLContentType" "PublicationURL"
               "Type" "VIEW RELATED INFORMATION"
               "URLValue" "https://lpdaacsvc.cr.usgs.gov/appeears/"}
          concept-src1 (tool-util/make-tool-concept)
          concept-result1 (as-> concept-src1 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls "URL" url1)
                                (json/generate-string intermediate)
                                (assoc concept-src1 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src2 (tool-util/make-tool-concept)
          concept-result2 (as-> concept-src2 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls "URL" url2)
                                (json/generate-string intermediate)
                                (assoc concept-src2 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          status1 (:status concept-result1)
          errors1 (:errors concept-result1)
          status2 (:status concept-result2)
          errors2 (:errors concept-result2)]
      (is (= 400 status1))
      (is (= 3 (count errors1)))
      (is (= 400 status2))
      (is (= 2 (count errors2))))))

(deftest related-url-format-mimetype-check
  (testing "Check valid and invalid Format and MimeType in RelatedURLs at ingest."
    (let [related-urls-valid [{"URL" "https://example.gov/good"
                               "URLContentType" "PublicationURL"
                               "Type" "VIEW RELATED INFORMATION"
                               "Subtype" "HOW-TO"
                               "Format" "ASCII"
                               "MimeType" "application/xml"}]
          related-urls-invalid1 [{"URL" "https://example.gov/one-error"
                                  "URLContentType" "PublicationURL"
                                  "Type" "VIEW RELATED INFORMATION"
                                  "Subtype" "HOW-TO"
                                  "Format" "invalid"
                                  "MimeType" "application/xml"}]
          related-urls-invalid2 [{"URL" "https://example.gov/one-error"
                                  "URLContentType" "PublicationURL"
                                  "Type" "VIEW RELATED INFORMATION"
                                  "Subtype" "HOW-TO"
                                  "Format" "ASCII"
                                  "MimeType" "invalid"}]
          related-urls-invalid3 [{"URL" "https://example.gov/two-errors"
                                  "URLContentType" "PublicationURL"
                                  "Type" "VIEW RELATED INFORMATION"
                                  "Subtype" "HOW-TO"
                                  "Format" "invalid"
                                  "MimeType" "invalid"}]
          concept-src1 (tool-util/make-tool-concept)
          concept-result1 (as-> concept-src1 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-valid)
                                (json/generate-string intermediate)
                                (assoc concept-src1 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src2 (tool-util/make-tool-concept)
          concept-result2 (as-> concept-src2 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-invalid1)
                                (json/generate-string intermediate)
                                (assoc concept-src2 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src3 (tool-util/make-tool-concept)
          concept-result3 (as-> concept-src3 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-invalid2)
                                (json/generate-string intermediate)
                                (assoc concept-src3 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src4 (tool-util/make-tool-concept)
          concept-result4 (as-> concept-src4 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-invalid3)
                                (json/generate-string intermediate)
                                (assoc concept-src4 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          status1 (:status concept-result1)
          errors1 (:errors concept-result1)
          status2 (:status concept-result2)
          errors2 (:errors concept-result2)
          status3 (:status concept-result3)
          errors3 (:errors concept-result3)
          status4 (:status concept-result4)
          errors4 (:errors concept-result4)]
      ;; The first RelatedURLs contains a valid Format and MimeType so no errors.
      (is (= 201 status1))
      (is (= 0 (count errors1)))

      ;; The second RelatedURLs contains an invalid Format, so the result contains one error.
      (is (= 400 status2))
      (is (= 1 (count errors2)))
      (is (= [{:path ["RelatedUrLs" 0 "Format"], :errors ["Format [invalid] was not a valid keyword."]}] errors2))
     
      ;; The third RelatedURLs contains an invalid MimeType, so the result contains one error. 
      (is (= 400 status3))
      (is (= 1 (count errors3)))
      (is (= [{:path ["RelatedUrLs" 0 "MimeType"], :errors ["MimeType [invalid] was not a valid keyword."]}] errors3))
     
      ;; The last RelatedURLs contains an invalid Format and an invalid MimeType, so the result contains two errors. 
      (is (= 400 status4))
      (is (= 2 (count errors4)))
      (is (= [{:path ["RelatedUrLs" 0 "Format"], :errors ["Format [invalid] was not a valid keyword."]}
              {:path ["RelatedUrLs" 0 "MimeType"], :errors ["MimeType [invalid] was not a valid keyword."]}] errors4)))))
