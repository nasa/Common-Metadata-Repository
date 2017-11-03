(ns cmr.system-int-test.ingest.bulk-update.bulk-update-endpoint-test
  "CMR bulk update endpoint integration tests"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
                                          {:grant-all-ingest? false}))

(def test-body
  "Default request body to use for testing"
  {:concept-ids ["C1", "C2", "C3"]
   :update-type "ADD_TO_EXISTING"
   :update-field "SCIENCE_KEYWORDS"
   :update-value {:Category "EARTH SCIENCE"
                  :Topic "HUMAN DIMENSIONS"
                  :Term "ENVIRONMENTAL IMPACTS"
                  :VariableLevel1 "HEAVY METALS CONCENTRATION"}})

(defn- grant-permissions-create-token
  "Test setup to create read/update ingest permissions for bulk update and
  return a token. Bulk update uses update permissions for the actual bulk update
  and read permissions for checking status."
  []
  (let [prov-admin-read-update-group-concept-id (e/get-or-create-group (s/context) "prov-admin-read-update-group")]
    (e/grant-group-provider-admin (s/context) prov-admin-read-update-group-concept-id "PROV1" :read :update)
    ;; Create and return token
    (e/login (s/context) "prov-admin-read-update" [prov-admin-read-update-group-concept-id])))

(deftest bulk-update-collection-endpoint-validation
  (testing "Invalid provider"
    (let [token (grant-permissions-create-token)
          response (ingest/bulk-update-collections "PROV-X" {} {:token token})
          {:keys [status errors]} response]
      (is (= 422 status))
      (is (= ["Provider with provider-id [PROV-X] does not exist."]
             errors))))
  (testing "System ACL"
    (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
      (is (= 401 status))
      (is (= ["You do not have permission to perform that action."]
             errors))))
  (testing "No provider permissions"
    (let [token (e/login (s/context) "prov-admin-read-update" ["prov-admin-read-update-group-guid"])]
      (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {:token token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors)))))
  (testing "Read permissions only"
    (e/grant-group-provider-admin (s/context) "prov-admin-read-update-group-guid" "provguid1" :read)
    (let [token (e/login (s/context) "prov-admin-read-update" ["prov-admin-read-update-group-guid"])]
      (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {:token token})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors))))))

(deftest bulk-update-collection-endpoint-body-validation
  (let [token (grant-permissions-create-token)]
    (testing "Request body validation"
      (are3 [body status-code error-messages]
            (let [response (ingest/bulk-update-collections "PROV1" body {:token token})
                  {:keys [status errors]} response]
              (is (= status-code status))
              (is (= error-messages errors)))

            "Missing concept-ids"
            {:name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "ADD_TO_EXISTING"}
            400
            ["object has missing required properties ([\"concept-ids\"])"]

            "0 concept-ids"
            {:concept-ids []
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "ADD_TO_EXISTING"}
            400
            ["/concept-ids array is too short: must have at least 1 elements but instance has 0 elements"]

            "Missing update field"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-type "ADD_TO_EXISTING"}
            400
            ["object has missing required properties ([\"update-field\"])"]

            "Missing update type"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"}
            400
            ["object has missing required properties ([\"update-type\"])"]

            "Invalid update type"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "REPLACE"}
            400
            ["/update-type instance value (\"REPLACE\") not found in enum (possible values: [\"ADD_TO_EXISTING\",\"CLEAR_FIELD\",\"CLEAR_ALL_AND_REPLACE\",\"FIND_AND_REMOVE\",\"FIND_AND_REPLACE\",\"FIND_AND_UPDATE\",\"FIND_AND_UPDATE_HOME_PAGE_URL\"])"]

            "Missing update value"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "FIND_AND_REPLACE"}
            400
            ["An update value must be supplied when the update is of type FIND_AND_REPLACE"]

            "Missing find value"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "FIND_AND_REPLACE"
             :update-value {:Category "EARTH SCIENCE"
                            :Topic "HUMAN DIMENSIONS"
                            :Term "ENVIRONMENTAL IMPACTS"
                            :VariableLevel1 "HEAVY METALS CONCENTRATION"}}
            400
            ["A find value must be supplied when the update is of type FIND_AND_REPLACE"]

            "update-value can not be an array for update-type not being ADD_TO_EXISTING"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "FIND_AND_REPLACE"
             :find-value {:Category "EARTH SCIENCE"}
             :update-value [{:Category "EARTH SCIENCE"
                            :Topic "HUMAN DIMENSIONS"
                            :Term "ENVIRONMENTAL IMPACTS"
                            :VariableLevel1 "HEAVY METALS CONCENTRATION"}]}
            400
            ["An update value must be a single object for the [FIND_AND_REPLACE] update type. Arrays are only supported for the ADD_TO_EXISTING update type."]

            "FIND_AND_UPDATE_HOME_PAGE_URL can not be used for update-field not being DATA_CENTERS"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "SCIENCE_KEYWORDS"
             :update-type "FIND_AND_UPDATE_HOME_PAGE_URL"
             :find-value {:Category "EARTH SCIENCE"}
             :update-value [{:Category "EARTH SCIENCE"
                            :Topic "HUMAN DIMENSIONS"
                            :Term "ENVIRONMENTAL IMPACTS"
                            :VariableLevel1 "HEAVY METALS CONCENTRATION"}]}
            400
            ["FIND_AND_UPDATE_HOME_PAGE_URL update type can not be used for the [SCIENCE_KEYWORDS] update field. It can only be used for the DATA_CENTERS update field."]

            "Invalid update field"
            {:concept-ids ["C1", "C2", "C3"]
             :name "TEST NAME"
             :update-field "Science keywords"
             :update-type "ADD_TO_EXISTING"
             :update-value {:Category "EARTH SCIENCE"}}
            400
            ["/update-field instance value (\"Science keywords\") not found in enum (possible values: [\"SCIENCE_KEYWORDS\",\"LOCATION_KEYWORDS\",\"DATA_CENTERS\",\"PLATFORMS\",\"INSTRUMENTS\"])"]))))

            ;; Short-name/version currently not supported. Support will be added
            ;; back in with CMR-4129

            ; "Missing collection identifiers"
            ; {:update-field "SCIENCE_KEYWORDS"
            ;  :update-type "ADD_TO_EXISTING"}
            ; 400
            ; ["instance failed to match exactly one schema (matched 0 out of 2)"
            ;  "object has missing required properties ([\"concept-ids\"])"
            ;  "object has missing required properties ([\"collection-ids\"])"]
            ;
            ; "0 collection ids"
            ; {:collection-ids []
            ;  :update-field "SCIENCE_KEYWORDS"
            ;  :update-type "ADD_TO_EXISTING"}
            ; 400
            ; ["/collection-ids array is too short: must have at least 1 elements but instance has 0 elements"]
            ;
            ; "Missing short-name"
            ; {:collection-ids [{:version "V1"}]
            ;  :update-field "SCIENCE_KEYWORDS"
            ;  :update-type "ADD_TO_EXISTING"}
            ; 400
            ; ["/collection-ids/0 object has missing required properties ([\"short-name\"])"]
            ;
            ; "Missing version"
            ; {:collection-ids [{:short-name "ABC"}]
            ;  :update-field "SCIENCE_KEYWORDS"
            ;  :update-type "ADD_TO_EXISTING"}
            ; 400
            ; ["/collection-ids/0 object has missing required properties ([\"version\"])"]))))

(deftest bulk-update-status-endpoint-validation
  (testing "Invalid provider"
    (let [token (grant-permissions-create-token)
          response (ingest/bulk-update-provider-status "PROV-X" {:token token})
          {:keys [status errors]} response]
      (is (= 422 status))
      (is (= ["Provider with provider-id [PROV-X] does not exist."]
             errors))))
  (testing "System ACL"
    (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
      (is (= 401 status))
      (is (= ["You do not have permission to perform that action."]
             errors))))
  (testing "No provider permissions"
    (let [token (e/login (s/context) "prov-admin-read-update" ["prov-admin-read-update-group-guid"])]
      (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors)))))
  (testing "Update permissions only"
    (e/grant-group-provider-admin (s/context) "prov-admin-read-update-group-guid" "provguid1" :update)
    (let [token (e/login (s/context) "prov-admin-read-update" ["prov-admin-read-update-group-guid"])]
      (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors))))))

(deftest bulk-update-task-status-endpoint-validation
  (testing "Invalid provider"
    (let [token (grant-permissions-create-token)
          response (ingest/bulk-update-task-status "PROV-X" "1" {:token token})
          {:keys [status errors]} response]
      (is (= 422 status))
      (is (= ["Provider with provider-id [PROV-X] does not exist."]
             errors))))
  (testing "System ACL"
    (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
      (is (= 401 status))
      (is (= ["You do not have permission to perform that action."]
             errors))))
  (testing "No provider permissions"
    (let [token (e/login (s/context) "prov-admin-read-update" ["prov-admin-read-update-group-guid"])]
      (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors)))))
  (testing "Update permissions only"
    (e/grant-group-provider-admin (s/context) "prov-admin-read-update-group-guid" "provguid1" :update)
    (let [token (e/login (s/context) "prov-admin-read-update" ["prov-admin-read-update-group-guid"])]
      (let [{:keys [status errors]} (ingest/bulk-update-collections "PROV1" {})]
        (is (= 401 status))
        (is (= ["You do not have permission to perform that action."]
               errors)))))
  (testing "Invalid task id"
    (let [token (grant-permissions-create-token)
          response (ingest/bulk-update-task-status "PROV1" 12 {:token token})
          {:keys [status errors]} response]
      (is (= 404 status))
      (is (= ["Bulk update task with task id [12] could not be found."]
             errors)))))
