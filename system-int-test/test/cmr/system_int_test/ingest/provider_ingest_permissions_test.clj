(ns cmr.system-int-test.ingest.provider-ingest-permissions-test
  "Verifies the correct provider ingest permissions are enforced"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.echo-util :as e]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"} false false))

(defn- ingest-succeeded?
  "Returns true if the provided token has permission to perform the given function."
  [response]
  (let [status (:status response)]
    (is (some #{status} [200 201 204 401]))
    (not= status 401)))

(deftest ingest-provider-management-permissions-test
  ;; Grant provider admin permission
  (e/grant-group-provider-admin "prov-admin-read-group-guid" "provguid1" :read)
  (e/grant-group-provider-admin "prov-admin-update-group-guid" "provguid1" :update)
  (e/grant-group-provider-admin "prov-admin-read-update-group-guid" "provguid1" :read :update)
  (e/grant-group-provider-admin "prov-admin-update-delete-group-guid" "provguid1" :delete :update)
  ;; Grant provider admin permission but for a different provider
  (e/grant-group-provider-admin "another-prov-admin-group-guid" "provguid2" :read :update :delete)
  ;; Grant system admin permission - but not provider admin
  (e/grant-group-admin "ingest-super-admin-group-guid" :read :update :delete)

  (let [guest-token (e/login-guest)
        user-token (e/login "user1" ["plain-group-guid2" "plain-group-guid3"])
        provider-admin-read-token (e/login "prov-admin-read" ["prov-admin-read-group-guid"
                                                              "plain-group-guid3"])
        provider-admin-update-token (e/login "prov-admin-update" ["prov-admin-update-group-guid"
                                                                  "plain-group-guid3"])
        provider-admin-read-update-token (e/login "prov-admin-read-update"
                                                  ["prov-admin-read-update-group-guid"
                                                   "plain-group-guid3"])
        provider-admin-update-delete-token (e/login "prov-admin-update-delete"
                                                    ["prov-admin-update-delete-group-guid"
                                                     "plain-group-guid3"])
        another-prov-admin-token (e/login "another-prov-admin" ["another-prov-admin-group-guid"
                                                                "plain-group-guid3"])
        super-admin-token (e/login "super-admin" ["ingest-super-admin-group-guid"])
        collection (d/ingest "PROV1" (dc/collection {}) :echo10 provider-admin-update-token)
        ingested-concept (ingest/get-concept (:concept-id collection))

        granule (dg/umm-granule->granule-concept (dg/granule collection))]

    (testing "ingest granule update permissions"
      (are [token]
           (ingest-succeeded? (ingest/ingest-concept granule token))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (not (ingest-succeeded? (ingest/ingest-concept granule token)))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest granule delete permissions"
      (is (ingest-succeeded? (ingest/delete-concept granule provider-admin-update-delete-token)))
      (are [token]
           (not (ingest-succeeded? (ingest/delete-concept granule token)))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-read-token))

    (testing "ingest collection update permissions"
      (are [token]
           (ingest-succeeded? (d/ingest "PROV1" (dc/collection {}) :echo10 token))
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-update-delete-token)
      (are [token]
           (not (ingest-succeeded? (d/ingest "PROV1" (dc/collection {}) :echo10 token)))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-read-token))

    (testing "ingest collection delete permissions"
      (is (ingest-succeeded?
            (ingest/delete-concept ingested-concept provider-admin-update-delete-token)))
      (are [token]
           (not (ingest-succeeded? (ingest/delete-concept ingested-concept token)))
           guest-token
           user-token
           super-admin-token
           another-prov-admin-token
           provider-admin-update-token
           provider-admin-read-update-token
           provider-admin-read-token))))