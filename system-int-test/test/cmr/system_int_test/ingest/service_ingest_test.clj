(ns cmr.system-int-test.ingest.service-ingest-test
  "CMR service ingest integration tests.
  For service permissions tests, see `provider-ingest-permissions-test`."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.service-util :as service-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest service-ingest-test
  (testing "ingest of a new service concept"
    (let [concept (service-util/make-service-concept)
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (mdb/concept-exists-in-mdb? concept-id revision-id))
      (is (= 1 revision-id))))
  (testing "ingest of a service concept with a revision id"
    (let [concept (service-util/make-service-concept {} {:revision-id 5})
          {:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
      (is (= 5 revision-id))
      (is (mdb/concept-exists-in-mdb? concept-id 5)))))

;; Verify that the accept header works
(deftest service-ingest-accept-header-response-test
  (let [supplied-concept-id "S1000-PROV1"]
    (testing "json response"
      (let [response (ingest/ingest-concept
                      (service-util/make-service-concept
                       {:concept-id supplied-concept-id})
                      {:accept-format :json
                       :raw? true})]
        (is (= {:revision-id 1
                :concept-id supplied-concept-id}
               (dissoc (ingest/parse-ingest-body :json response) :body)))))

    (testing "xml response"
      (let [response (ingest/ingest-concept
                      (service-util/make-service-concept
                       {:concept-id supplied-concept-id})
                      {:accept-format :xml
                       :raw? true})]
        (is (= {:revision-id 2
                :concept-id supplied-concept-id}
               (dissoc (ingest/parse-ingest-body :xml response) :body)))))))

;; Verify that the accept header works with returned errors
(deftest service-ingest-with-errors-accept-header-test
  (testing "json response"
    (let [concept-no-metadata (assoc (service-util/make-service-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :json
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :json response)]
      (is (re-find #"Request content is too short." (first errors)))))
  (testing "xml response"
    (let [concept-no-metadata (assoc (service-util/make-service-concept)
                                     :metadata "")
          response (ingest/ingest-concept
                    concept-no-metadata
                    {:accept-format :xml
                     :raw? true})
          {:keys [errors]} (ingest/parse-ingest-body :xml response)]
      (is (re-find #"Request content is too short." (first errors))))))

;; Verify that user-id is saved from User-Id or token header
(deftest service-ingest-user-id-test
  (testing "ingest of new concept"
    (are3 [ingest-headers expected-user-id]
      (let [concept (service-util/make-service-concept)
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
      (let [concept (service-util/make-service-concept)
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
(deftest service-w-concept-id-ingest-test
  (let [supplied-concept-id "S1000-PROV1"
        metadata {:concept-id supplied-concept-id
                  :native-id "Atlantic-1"}
        concept (service-util/make-service-concept metadata)]
    (testing "ingest of a new service concept with concept-id present"
      (let [{:keys [concept-id revision-id]} (ingest/ingest-concept concept)]
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= [supplied-concept-id 1] [concept-id revision-id]))))
    (testing "ingest of same native id and different providers is allowed"
      (let [concept2-id "S1000-PROV2"
            concept2 (service-util/make-service-concept
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

(deftest service-ingest-schema-validation-test
  (testing "ingest of service concept JSON schema validation missing field"
    (let [concept (service-util/make-service-concept {:Type ""})
          {:keys [status errors]} (ingest/ingest-concept concept)]
      (is (= 400 status))
      (is (= ["#/Type:  is not a valid enum value"]
             errors))))
  (testing "ingest of service concept JSON schema validation invalid field"
    (let [concept (service-util/make-service-concept {:InvalidField "xxx"})
          {:keys [status errors]} (ingest/ingest-concept concept)]
      (is (= 400 status))
      (is (= ["#: extraneous key [InvalidField] is not permitted"]
             errors)))))

(deftest service-update-error-test
  (let [supplied-concept-id "S1000-PROV1"
        concept (service-util/make-service-concept
                 {:concept-id supplied-concept-id
                  :native-id "Atlantic-1"})
        _ (ingest/ingest-concept concept)]
    (testing "update concept with a different concept-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :concept-id "S1111-PROV1"))]
        (is (= [409 [(str "A concept with concept-id [S1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:service] provider-id [PROV1]. "
                          "The given concept-id [S1111-PROV1] and native-id "
                          "[Atlantic-1] would conflict with that one.")]]
               [status errors]))))
    (testing "update concept with a different native-id is invalid"
      (let [{:keys [status errors]} (ingest/ingest-concept
                                     (assoc concept :native-id "other"))]
        (is (= [409 [(str "A concept with concept-id [S1000-PROV1] and "
                          "native-id [Atlantic-1] already exists for "
                          "concept-type [:service] provider-id [PROV1]. "
                          "The given concept-id [S1000-PROV1] and native-id "
                          "[other] would conflict with that one.")]]
               [status errors]))))))

(deftest delete-service-ingest-test
  (testing "delete a service"
    (let [concept (service-util/make-service-concept)
          _ (service-util/ingest-service concept)
          {:keys [status concept-id revision-id]}  (ingest/delete-concept concept)
          fetched (mdb/get-concept concept-id revision-id)]
      (is (= 200 status))
      (is (= 2 revision-id))
      (is (= (:native-id concept)
             (:native-id fetched)))
      (is (:deleted fetched))
      (testing "delete a deleted service"
        (let [{:keys [status errors]} (ingest/delete-concept concept)]
          (is (= [status errors]
                 [404 [(format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                               (:native-id concept) concept-id)]]))))
      (testing "create a service over a service's tombstone"
        (let [response (service-util/ingest-service
                        (service-util/make-service-concept))
              {:keys [status concept-id revision-id]} response]
          (is (= 200 status))
          (is (= 3 revision-id)))))))

(deftest related-url-content-type-type-and-subtype-check
  (testing
    "Check valid and invalid related urls url content type, type, and subtype.
    The first URL is good while the second is bad. The error response should
    represent this showing the second URL type failed to match a valid keyword."
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
          concept-src (service-util/make-service-concept)
          concept-result (as-> concept-src intermediate
                               (json/parse-string (:metadata intermediate))
                               (assoc intermediate "RelatedURLs" related-urls)
                               (json/generate-string intermediate)
                               (assoc concept-src :metadata intermediate)
                               (ingest/ingest-concept intermediate))
          {:keys [status errors]} concept-result]
      (is (= 400 status))
      (is (= 2 (count errors)))

      (are3 [expected-content expected-type expected-subtype index error-item]
        (let [expected-msg (format "Related URL Content Type, Type, and Subtype [%s>%s>%s] are not a valid set together."
                                   expected-content expected-type expected-subtype)
              error-path (:path error-item)]
          (is (= expected-msg (first (:errors error-item))))
          (is (= "relatedurls" (clojure.string/lower-case (first error-path))))
          (is (= index (second error-path))))

        "First URL with bad keyword pair"
        "PublicationURL"
        "USE SERVICE APIs"
        "Closed Search"
        1
        (first errors)

        "Second URL with bad keyword pair"
        "PublicationURL"
        "USE SERVICE APIs"
        "Very Closed Search"
        2
        (second errors)))))

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
          concept-src1 (service-util/make-service-concept)
          concept-result1 (as-> concept-src1 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-valid)
                                (json/generate-string intermediate)
                                (assoc concept-src1 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src2 (service-util/make-service-concept)
          concept-result2 (as-> concept-src2 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-invalid1)
                                (json/generate-string intermediate)
                                (assoc concept-src2 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src3 (service-util/make-service-concept)
          concept-result3 (as-> concept-src3 intermediate
                                (json/parse-string (:metadata intermediate))
                                (assoc intermediate "RelatedURLs" related-urls-invalid2)
                                (json/generate-string intermediate)
                                (assoc concept-src3 :metadata intermediate)
                                (ingest/ingest-concept intermediate))
          concept-src4 (service-util/make-service-concept)
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
      (is (= [{:path ["RelatedUrLs" 0 "Format"], :errors ["Format [invalid] was not a valid keyword."]} {:path ["RelatedUrLs" 0 "MimeType"], :errors ["MimeType [invalid] was not a valid keyword."]}] errors4)))))
