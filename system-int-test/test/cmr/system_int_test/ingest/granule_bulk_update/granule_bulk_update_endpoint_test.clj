(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-endpoint-test
  "CMR granule bulk update endpoint integration tests"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}
                                         {:grant-all-ingest? false}))

(defn- grant-permissions-create-token
  "Test setup to create read/update ingest permissions for bulk update and
  return a token. Bulk update uses update permissions for the actual bulk update
  and read permissions for checking status."
  []
  (let [prov-admin-read-update-group-concept-id (echo-util/get-or-create-group (system/context) "prov-admin-read-update-group")]
    (echo-util/grant-group-provider-admin (system/context) prov-admin-read-update-group-concept-id "PROV1" :read :update)
    ;; Create and return token
    (echo-util/login (system/context) "prov-admin-read-update" [prov-admin-read-update-group-concept-id])))

(deftest bulk-update-granule-endpoint-body-validation
  (let [token (grant-permissions-create-token)]
    (testing "Request body validation"
      (are3 [body status-code error-messages]
        (let [response (ingest/bulk-update-granules "PROV1" body {:token token})
              {:keys [status errors]} response]
          (is (= status-code status))
          (is (any? (map #(re-find error-messages %) errors))))

        "Invalid identifiers"
        {:name "bulk update prov1 granules"
         :update-field "foo"
         :operation "UPDATE_FIELD"
         :ch-ch-ch-ch-chaaaaaanges [["Turn and face the strange" "ch-ch-changes"]
                                    ["Time may change me" "But I can't change time"]]}
        400
        #"invalid identifier"))))
