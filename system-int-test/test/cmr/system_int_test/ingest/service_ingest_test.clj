(ns cmr.system-int-test.ingest.service-ingest-test
  "CMR service ingest integration tests."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.service-util :as service-util]))

(use-fixtures :each (ingest-util/reset-fixture))

(deftest service-ingest-test
  (testing "ingest of a new concept"
    (let [;; Groups
          update-group-id (e/get-or-create-group (s/context) "umm-var-guid1")
          ;; Tokens
          update-token (e/login (s/context) "umm-var-user1" [update-group-id])
          ;; Grants
          update-grant-id (e/grant-group-admin
                           (assoc (s/context) :token update-token)
                           update-group-id
                           :update)
          service (service-util/make-service)]
      (is (e/permitted? update-token
                        update-grant-id
                        update-group-id
                        (ingest-util/get-ingest-update-acls update-token)))
      (let [{:keys [status concept-id revision-id]} (service-util/create-service
                                                     update-token service)]
        (is (= 201 status))
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))))

(deftest service-ingest-permissions-test
  (testing "ingest permissions"
    (let [;; Groups
          guest-group-id (e/get-or-create-group
                          (s/context) "umm-var-guid1")
          reg-user-group-id (e/get-or-create-group
                             (s/context) "umm-var-guid2")
          update-group-id (e/get-or-create-group
                           (s/context) "umm-var-guid3")
          ;; Tokens
          guest-token (e/login
                       (s/context) "umm-var-user1" [guest-group-id])
          reg-user-token (e/login
                          (s/context) "umm-var-user1" [reg-user-group-id])
          update-token (e/login
                        (s/context) "umm-var-user2" [update-group-id])
          ;; Grants
          guest-grant-id (e/grant
                          (assoc (s/context) :token guest-token)
                          [{:permissions [:read]
                            :user_type :guest}]
                          :system_identity
                          {:target nil})
          reg-user-grant-id (e/grant
                             (assoc (s/context) :token reg-user-token)
                             [{:permissions [:read]
                               :user_type :registered}]
                             :system_identity
                             {:target nil})
          update-grant-id (e/grant-group-admin
                           (assoc (s/context) :token update-token)
                           update-group-id
                           :update)
          service (service-util/make-service)]
      (testing "acl setup and grants for different users"
        (is (e/not-permitted? guest-token
                              guest-grant-id
                              guest-group-id
                              (ingest-util/get-ingest-update-acls guest-token)))
        (is (e/not-permitted? reg-user-token
                              reg-user-grant-id
                              reg-user-group-id
                              (ingest-util/get-ingest-update-acls reg-user-token)))
        (is (e/permitted? update-token
                          update-grant-id
                          update-group-id
                          (ingest-util/get-ingest-update-acls update-token))))
      (testing "ingest service creation permissions"
        (are3 [token expected]
          (let [response (service-util/create-service token service)]
            (is (= expected (:status response))))
          "System update permission allowed"
          update-token 201
          "Regular user denied"
          reg-user-token 401
          "Guest user denied"
          guest-token 401)))))
