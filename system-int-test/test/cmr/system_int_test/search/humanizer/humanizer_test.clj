(ns cmr.system-int-test.search.humanizer.humanizer-test
  "This tests the CMR Search API's humanizers capabilities"
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.humanizer-util :as hu]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.test.expected-conversion :as exp-conv]))

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
           (hu/update-humanizers nil (hu/make-humanizers)))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token ABC does not exist"]}
           (hu/update-humanizers "ABC" (hu/make-humanizers)))))

  (testing "Create without permission"
    (let [token (e/login (s/context) "user2")]
      (is (= {:status 401
              :errors ["You do not have permission to perform that action."]}
             (hu/update-humanizers token (hu/make-humanizers)))))))

(deftest update-humanizers-validation-test
  (let [admin-update-group-concept-id (e/get-or-create-group (s/context) "admin-update-group")
        _ (e/grant-group-admin (s/context) admin-update-group-concept-id :update)
        admin-update-token (e/login (s/context) "admin" [admin-update-group-concept-id])
        valid-humanizers (hu/make-humanizers)
        valid-humanizer-rule (first valid-humanizers)]
    (testing "Create humanizer with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (hu/update-humanizers admin-update-token valid-humanizers {:http-options {:content-type :xml}}))))

    (testing "Create humanizer with nil body"
      (is (= {:status 400,
              :errors
              ["instance type (null) does not match any allowed primitive type (allowed: [\"array\"])"]}
             (hu/update-humanizers admin-update-token nil))))

    (testing "Create humanizer with empty array"
      (is (= {:status 400,
              :errors
              ["array is too short: must have at least 1 elements but instance has 0 elements"]}
             (hu/update-humanizers admin-update-token []))))

    (testing "Missing field validations"
      (are [field]
        (= {:status 400
            :errors [(format "/0 object has missing required properties ([\"%s\"])"
                             (name field))]}
           (hu/update-humanizers admin-update-token [(dissoc valid-humanizer-rule field)]))

        :type :field))

    (testing "Minimum field length validations"
      (are [field]
        (= {:status 400
            :errors [(format "/0/%s string \"\" is too short (length: 0, required minimum: 1)"
                             (name field))]}
           (hu/update-humanizers admin-update-token [(assoc valid-humanizer-rule field "")]))

        :type :field))

    (testing "Maximum field length validations"
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format
                            "/0/%s string \"%s\" is too long (length: %d, maximum allowed: %d)"
                            (name field) long-value (inc max-length) max-length)]}
                 (hu/update-humanizers
                  admin-update-token [(assoc valid-humanizer-rule field long-value)]))))))))

(deftest update-humanizers-test
  (testing "Successful creation"
    (let [admin-update-group-concept-id (e/get-or-create-group (s/context) "admin-update-group")
          _  (e/grant-group-admin (s/context) admin-update-group-concept-id :update)
          token (e/login (s/context) "admin" [admin-update-group-concept-id])
          humanizers (hu/make-humanizers)
          {:keys [status concept-id revision-id]} (hu/update-humanizers token humanizers)]
      (is (= 201 status))
      (is concept-id)
      (is (= 1 revision-id))
      (hu/assert-humanizers-saved {:humanizers humanizers} "admin" concept-id revision-id)

      (testing "Successful update"
        (let [existing-concept-id concept-id
              updated-humanizers [(second humanizers)]
              {:keys [status concept-id revision-id]} (hu/update-humanizers token updated-humanizers)]
          (is (= 200 status))
          (is (= existing-concept-id concept-id))
          (is (= 2 revision-id))
          (hu/assert-humanizers-saved {:humanizers updated-humanizers} "admin" concept-id revision-id)))))

  (testing "Create humanizer with fields at maximum length"
    (let [token (e/login (s/context) "admin" ["admin-update-group-guid"])
          humanizers [(into {} (for [[field max-length] field-maxes]
                                 [field (string-of-length max-length)]))]]
      (is (= 200 (:status (hu/update-humanizers token humanizers)))))))


(deftest get-humanizers-test
  (testing "Get humanizer"
    (let [admin-update-group-concept-id (e/get-or-create-group (s/context) "admin-update-group")
          _  (e/grant-group-admin (s/context) admin-update-group-concept-id :update)
          humanizers (hu/make-humanizers)
          token (e/login (s/context) "admin" [admin-update-group-concept-id])
          _ (hu/update-humanizers token humanizers)
          expected-humanizers {:status 200
                               :body humanizers}]

      (is (= expected-humanizers (hu/get-humanizers))))))

(deftest humanizer-report-test
  (testing "Humanizer report saved successfully"
    (let [humanizers (hu/make-humanizers)
          ;; Ingest humanizers
          _ (hu/update-humanizers transmit-config/mock-echo-system-token humanizers)
          returned-humanizers (:body (hu/get-humanizers))
          ;; sanity check
          _ (is (= humanizers returned-humanizers))
          ;; Ingest collections that will use those humanizers
          coll1 (d/ingest-umm-spec-collection
                  "PROV1"
                  (assoc exp-conv/example-collection-record
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
        (let [humanizers (conj (hu/make-humanizers)
                               {:source_value "Term2"
                                :replacement_value "Best Term Ever"
                                :field "science_keyword"
                                :type "alias"
                                :reportable true
                                :order 0})
              _ (hu/update-humanizers transmit-config/mock-echo-system-token humanizers)
              returned-humanizers (:body (hu/get-humanizers))
              ;; sanity check
              _ (is (= humanizers returned-humanizers))
              coll2 (d/ingest-umm-spec-collection
                      "PROV1"
                      (assoc exp-conv/example-collection-record
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
          (is (= expected-report2 regenerated-report)))))))

(deftest humanizer-report-permissions
  (testing "Anyone can request the humanizer report"
    ;; ingest a collection and refresh cache to avoid the wait and retry in the get-all-collections code.
    (d/ingest-umm-spec-collection
       "PROV1"
       (assoc exp-conv/example-collection-record
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
    (let [admin-group-id (e/get-or-create-group (s/context) "admin-group")
          admin-user-token (e/login (s/context) "admin-user" [admin-group-id])]
      (e/grant-group-admin (s/context) admin-group-id :update)
      ;; Need to clear the ACL cache to get the latest ACLs from mock-echo
      (search/clear-caches)
      ;; ingest a collection and refresh cache to avoid the wait and retry in the get-all-collections code.
      (d/ingest-umm-spec-collection
       "PROV1"
       (assoc exp-conv/example-collection-record
              :ScienceKeywords [{:Category "earth science"
                                 :Topic "Bioosphere"
                                 :Term "Term1"}]
              :concept-id "C1-PROV1")
        {:format :umm-json
         :accept-format :json})
    (index/wait-until-indexed)
    (search/refresh-collection-metadata-cache)
      (is (= 200 (:status (search/get-humanizers-report-raw {:regenerate true
                                                             :token admin-user-token})))))))
