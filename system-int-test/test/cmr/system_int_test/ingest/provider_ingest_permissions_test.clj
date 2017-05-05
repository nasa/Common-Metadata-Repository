(ns cmr.system-int-test.ingest.provider-ingest-permissions-test
  "Verifies the correct provider ingest permissions are enforced"
  (:require
    [clojure.test :refer :all]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                                          {:grant-all-search? false
                                           :grant-all-ingest? false}))

(defn- ingest-succeeded?
  "Returns true if the provided token has permission to perform the given function."
  [response]
  (let [status (:status response)]
    (is (some #{status} [200 201 204 401 404]))
    (not= status 401)))

(deftest ingest-provider-management-permissions-test
  (let [prov-admin-read-group-concept-id (e/get-or-create-group (s/context) "prov-admin-read-group")
        prov-admin-update-group-concept-id (e/get-or-create-group (s/context) "prov-admin-update-group")
        prov-admin-read-update-group-concept-id (e/get-or-create-group (s/context) "prov-admin-read-update-group")
        prov-admin-update-delete-group-concept-id (e/get-or-create-group (s/context) "prov-admin-update-delete-group")
        prov-admin-read-update-delete-group-concept-id (e/get-or-create-group (s/context) "prov-admin-read-update-delete-group")
        another-prov-admin-group-concept-id (e/get-or-create-group (s/context) "another-prov-admin-group")
        ingest-super-admin-group-concept-id (e/get-or-create-group (s/context) "ingest-super-admin-group")
        plain-group-concept-id2 (e/get-or-create-group (s/context) "plain-group-2")
        plain-group-concept-id3 (e/get-or-create-group (s/context) "plain-group-3")
        guest-token (e/login-guest (s/context))
        user-token (e/login (s/context) "user1" [plain-group-concept-id2 plain-group-concept-id3])
        provider-admin-read-token (e/login (s/context) "prov-admin-read" [prov-admin-read-group-concept-id
                                                                          plain-group-concept-id3])
        provider-admin-update-token (e/login (s/context) "prov-admin-update" [prov-admin-update-group-concept-id
                                                                              plain-group-concept-id3])
        provider-admin-read-update-token (e/login (s/context) "prov-admin-read-update"
                                                  [prov-admin-read-update-group-concept-id
                                                   plain-group-concept-id3])
        provider-admin-update-delete-token (e/login (s/context) "prov-admin-update-delete"
                                                    [prov-admin-update-delete-group-concept-id
                                                     plain-group-concept-id3])
        another-prov-admin-token (e/login (s/context) "another-prov-admin" [another-prov-admin-group-concept-id
                                                                            plain-group-concept-id3])
        super-admin-token (e/login (s/context) "super-admin" [ingest-super-admin-group-concept-id])
        non-existant-token "not-exist"

        ;; Grant provider admin permission
        _ (e/grant-group-provider-admin (s/context) prov-admin-read-group-concept-id "PROV1" :read)
        _ (e/grant-group-provider-admin (s/context) prov-admin-update-group-concept-id "PROV1" :update)
        _ (e/grant-group-provider-admin (s/context) prov-admin-read-update-group-concept-id "PROV1" :read :update)
        _ (e/grant-group-provider-admin (s/context) prov-admin-update-delete-group-concept-id "PROV1" :delete :update)
        ;; Grant provider admin permission but for a different provider
        _ (e/grant-group-provider-admin (s/context) another-prov-admin-group-concept-id "PROV2" :read :update :delete)
        ;; Grant system admin permission - but not provider admin
        _ (e/grant-group-admin (s/context) ingest-super-admin-group-concept-id :read :update :delete)

        collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {})  {:token provider-admin-update-token})
        ingested-concept (mdb/get-concept (:concept-id collection))

        granule (d/item->concept (dg/granule-with-umm-spec-collection collection "C1-PROV1"))]

    (testing "ingest granule update permissions"
      (are [token]
           (ingest-succeeded? (ingest/ingest-concept granule {:token token
                                                              :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)

      (are [token]
           (not (ingest-succeeded? (ingest/ingest-concept granule {:token token
                                                                   :allow-failure? true})))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest granule delete permissions"
      (are [token]
           (ingest-succeeded? (ingest/delete-concept granule {:token token
                                                              :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (not (ingest-succeeded? (ingest/delete-concept granule {:token token
                                                                   :allow-failure? true})))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest collection update permissions"
      (are [token]
           (ingest-succeeded? (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}) {:token token
                                                                            :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (not (ingest-succeeded? (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}) {:token token
                                                                                 :allow-failure? true})))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest collection delete permissions"
      (are [token]
           (ingest-succeeded? (ingest/delete-concept ingested-concept {:token token
                                                                       :allow-failure? true}))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (not (ingest-succeeded? (ingest/delete-concept ingested-concept {:token token
                                                                            :allow-failure? true})))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))))
