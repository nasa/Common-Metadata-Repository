(ns cmr.system-int-test.search.humanizer.humanizer-test
  "This tests the CMR Search API's humanizers capabilities"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.test.side-api :as side]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.humanizer-util :as humanizer-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [cmr.umm-spec.versioning :as versioning]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def field-maxes
  "A map of fields to their max lengths"
  {:type 255
   :field 255})

(defn string-of-length
  "Creates a string of the specified length"
  [n]
  (string/join (repeat n "x")))

(deftest update-humanizers-no-permission-test
  (testing "Create without token"
    (is (= {:status 401
            :errors ["You do not have permission to perform that action."]}
           (humanizer-util/update-humanizers nil (humanizer-util/make-humanizers)))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token does not exist"]}
           (humanizer-util/update-humanizers "ABC" (humanizer-util/make-humanizers)))))

  (testing "Create without permission"
    (let [token (echo-util/login (system/context) "user2")]
      (is (= {:status 401
              :errors ["You do not have permission to perform that action."]}
             (humanizer-util/update-humanizers token (humanizer-util/make-humanizers)))))))

(deftest update-humanizers-validation-test
  (let [admin-update-group-concept-id (echo-util/get-or-create-group
                                       (system/context)
                                       "admin-update-group")
        _ (echo-util/grant-group-admin
           (system/context)
           admin-update-group-concept-id
           :update)
        admin-update-token (echo-util/login
                            (system/context)
                            "admin"
                            [admin-update-group-concept-id])
        valid-humanizers (humanizer-util/make-humanizers)
        valid-humanizer-rule (first valid-humanizers)]
    (testing "Create humanizer with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (humanizer-util/update-humanizers
              admin-update-token
              valid-humanizers
              {:http-options {:content-type :xml}}))))

    (testing "Create humanizer with nil body"
      (is (= {:status 400,
              :errors
              ["#: expected type: JSONArray, found: Null"]}
             (humanizer-util/update-humanizers admin-update-token nil))))

    (testing "Create humanizer with empty array"
      (is (= {:status 400,
              :errors
              ["#: expected minimum item count: 1, found: 0"]}
             (humanizer-util/update-humanizers admin-update-token []))))

    (testing "Missing field validations"
      (are [field]
        (= {:status 400
            :errors [(format "#/0: required key [%s] not found"
                             (name field))]}
           (humanizer-util/update-humanizers
            admin-update-token
            [(dissoc valid-humanizer-rule field)]))

        :type :field))

    (testing "Minimum field length validations"
      (are [field]
        (= {:status 400
            :errors [(format "#/0/%s: expected minLength: 1, actual: 0"
                             (name field))]}
           (humanizer-util/update-humanizers
            admin-update-token
            [(assoc valid-humanizer-rule field "")]))

        :type :field))

    (testing "Maximum field length validations"
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format "#/0/%s: expected maxLength: %d, actual: %d"
                                   (name field)
                                   max-length
                                   (inc max-length))]}
                 (humanizer-util/update-humanizers
                  admin-update-token [(assoc valid-humanizer-rule field long-value)]))))))))

(deftest update-humanizers-test
  (testing "Successful creation"
    (let [admin-update-group-concept-id (echo-util/get-or-create-group
                                         (system/context)
                                         "admin-update-group")
          _  (echo-util/grant-group-admin
              (system/context)
              admin-update-group-concept-id
              :update)
          token (echo-util/login
                 (system/context)
                 "admin"
                 [admin-update-group-concept-id])
          humanizers (humanizer-util/make-humanizers)
          {:keys [status concept-id revision-id]} (humanizer-util/update-humanizers
                                                   token
                                                   humanizers)]
      (is (= 201 status))
      (is concept-id)
      (is (= 1 revision-id))
      (humanizer-util/assert-humanizers-saved
       {:humanizers humanizers}
       "admin"
       concept-id
       revision-id)

      (testing "Successful update"
        (let [existing-concept-id concept-id
              updated-humanizers [(second humanizers)]
              {:keys [status concept-id revision-id]} (humanizer-util/update-humanizers
                                                       token
                                                       updated-humanizers)]
          (is (= 200 status))
          (is (= existing-concept-id concept-id))
          (is (= 2 revision-id))
          (humanizer-util/assert-humanizers-saved
           {:humanizers updated-humanizers}
           "admin"
           concept-id
           revision-id)))))

  (testing "Create humanizer with fields at maximum length"
    (let [token (echo-util/login (system/context)
                                 "admin"
                                 ["admin-update-group-guid"])
          humanizers [(into {} (for [[field max-length] field-maxes]
                                 [field (string-of-length max-length)]))]]
      (is (= 200 (:status (humanizer-util/update-humanizers token humanizers)))))))


(deftest get-humanizers-test
  (testing "Get humanizer"
    (let [admin-update-group-concept-id (echo-util/get-or-create-group
                                         (system/context)
                                         "admin-update-group")
          _  (echo-util/grant-group-admin
              (system/context)
              admin-update-group-concept-id
              :update)
          humanizers (humanizer-util/make-humanizers)
          token (echo-util/login
                 (system/context)
                 "admin"
                 [admin-update-group-concept-id])
          _ (humanizer-util/update-humanizers token humanizers)
          expected-humanizers {:status 200
                               :body humanizers}]

      (is (= expected-humanizers (humanizer-util/get-humanizers))))))

(deftest humanizer-report-test
  (let [accepted-version (common-config/collection-umm-version)
        _ (side/eval-form `(common-config/set-collection-umm-version!
                          versioning/current-collection-version))]
  (testing "Humanizer report saved successfully"
    (let [humanizers (humanizer-util/make-humanizers)
          ;; Ingest humanizers
          _ (humanizer-util/update-humanizers
             transmit-config/mock-echo-system-token
             humanizers)
          returned-humanizers (:body (humanizer-util/get-humanizers))
          ;; sanity check
          _ (is (= humanizers returned-humanizers))
          ;; Ingest collections that will use those humanizers
          coll1 (data-core/ingest-umm-spec-collection
                  "PROV1"
                  (assoc expected-conversion/example-collection-record
                         :ScienceKeywords [{:Category "earth science"
                                             :Topic "Bioosphere"
                                             :Term "Term1"}]
                         :concept-id "C1-PROV1")
                  {:format :umm-json
                   :accept-format :json})

          _ (index/wait-until-indexed)
          ;; Humanizers use the cached collection metadata - clear to make sure we have the latest
          _ (search/refresh-collection-metadata-cache)
          expected-report1 (str "provider,concept_id,short_name,version,original_value,"
                                "humanized_value\n"
                                "PROV1,C1-PROV1,Short,V5,Bioosphere,Biosphere\n")
          initial-report (search/get-humanizers-report)]
      (is (= expected-report1 initial-report))

      (testing "Humanizer report can be force regenerated by an admin"
        (let [humanizers (conj (humanizer-util/make-humanizers)
                               {:source_value "Term2"
                                :replacement_value "Best Term Ever"
                                :field "science_keyword"
                                :type "alias"
                                :reportable true
                                :order 0})
              _ (humanizer-util/update-humanizers
                 transmit-config/mock-echo-system-token
                 humanizers)
              returned-humanizers (:body (humanizer-util/get-humanizers))
              ;; sanity check
              _ (is (= humanizers returned-humanizers))
              coll2 (data-core/ingest-umm-spec-collection
                      "PROV1"
                      (assoc expected-conversion/example-collection-record
                             :ShortName "NewSN"
                             :EntryTitle "New Entry title"
                             :ScienceKeywords [{:Category "earth science"
                                                 :Topic "Bioosphere"
                                                 :Term "Term2"}]

                             :concept-id "C2-PROV1")
                      {:format :umm-json
                       :accept-format :json})

              _ (index/wait-until-indexed)
              _ (search/refresh-collection-metadata-cache)
              expected-report2 (str "provider,concept_id,short_name,version,original_value,"
                                    "humanized_value\n"
                                    "PROV1,C1-PROV1,Short,V5,Bioosphere,Biosphere\n"
                                    "PROV1,C2-PROV1,NewSN,V5,Bioosphere,Biosphere\n"
                                    "PROV1,C2-PROV1,NewSN,V5,Term2,Best Term Ever\n")
              report-unchanged-by-recent-ingest (search/get-humanizers-report)
              regenerated-report (search/get-humanizers-report
                                  {:regenerate true :token transmit-config/mock-echo-system-token})]
          (is (= initial-report report-unchanged-by-recent-ingest))
          (is (= expected-report2 regenerated-report))))))
      (side/eval-form `(common-config/set-collection-umm-version! ~accepted-version))))

(deftest humanizer-report-permissions
  (let [accepted-version (common-config/collection-umm-version)
        _ (side/eval-form `(common-config/set-collection-umm-version!
                          versioning/current-collection-version))]
  (testing "Anyone can request the humanizer report"
    ;; ingest a collection and refresh cache to avoid the wait and retry in the get-all-collections code.
    (data-core/ingest-umm-spec-collection
       "PROV1"
       (assoc expected-conversion/example-collection-record
              :ScienceKeywords [{:Category "earth science"
                                 :Topic "Bioosphere"
                                 :Term "Term1"}]
              :concept-id "C1-PROV1")
        {:format :umm-json
         :accept-format :json})
    (index/wait-until-indexed)
    (search/refresh-collection-metadata-cache)
    (is (= 200 (:status (search/get-humanizers-report-raw)))))
  (testing "Guests cannot force the humanizer report to be regenerated"
    (is (= 401 (:status (search/get-humanizers-report-raw {:regenerate true})))))
  (testing "Users with system ingest update permission can regenerate the report"
    (let [admin-group-id (echo-util/get-or-create-group (system/context) "admin-group")
          admin-user-token (echo-util/login (system/context) "admin-user" [admin-group-id])]
      (echo-util/grant-group-admin (system/context) admin-group-id :update)
      ;; Need to clear the ACL cache to get the latest ACLs from mock-echo
      (search/clear-caches)
      ;; ingest a collection and refresh cache to avoid the wait and retry in the get-all-collections code.
      (data-core/ingest-umm-spec-collection
       "PROV1"
       (assoc expected-conversion/example-collection-record
              :ScienceKeywords [{:Category "earth science"
                                 :Topic "Bioosphere"
                                 :Term "Term1"}]
              :concept-id "C1-PROV1")
        {:format :umm-json
         :accept-format :json})
    (index/wait-until-indexed)
    (search/refresh-collection-metadata-cache)
      (is (= 200 (:status (search/get-humanizers-report-raw {:regenerate true
                                                             :token admin-user-token}))))))
  (side/eval-form `(common-config/set-collection-umm-version! ~accepted-version))))
