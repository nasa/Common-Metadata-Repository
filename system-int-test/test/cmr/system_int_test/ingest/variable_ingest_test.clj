(ns cmr.system-int-test.ingest.variable-ingest-test
  "CMR variable ingest integration tests."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each (ingest-util/reset-fixture))

(deftest variable-ingest-test
  (let [;; Groups
        update-group-id (e/get-or-create-group (s/context) "umm-var-guid1")
        ;; Tokens
        update-token (e/login (s/context) "umm-var-user1" [update-group-id])
        ;; Grants
        update-grant-id (e/grant-group-admin
                         (assoc (s/context) :token update-token)
                         update-group-id
                         :update)
        variable-data (variable-util/make-variable)]
    (is (e/permitted? update-token
                      update-grant-id
                      update-group-id
                      (ingest-util/get-ingest-update-acls update-token)))
    (testing "ingest: create a new variable"
      (let [{:keys [status concept-id revision-id]} (variable-util/create-variable
                                                     update-token variable-data)]
        (is (= 201 status))
        (is (= 1 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (let [fetched (mdb/get-concept concept-id revision-id)
              metadata (edn/read-string (:metadata fetched))]
          (is (= (:Name variable-data) (:native-id fetched)))
          (is (= (:LongName variable-data) (:long-name metadata)))
          ;; XXX Once CMR-4172 (metdata-db services support) was added, we tried to
          ;;     enable the following test; this required the work that we've since
          ;;     put into ticket CMR-4193, which in turn is probably blocked by an
          ;;     as-yet unfiled ticket for addressing what seems to be an ACL caching
          ;;     issue. Once those two tickets are resolved, this test will be
          ;;     enabled and should then pass.
          ; (variable-util/assert-variable-saved variable-data
          ;                                      "umm-var-user1"
          ;                                      concept-id
          ;                                      revision-id)
          )))
    (testing "ingest: update an existing variable"
      (let [new-long-name "A new long name"
            {:keys [status concept-id revision-id]} (variable-util/update-variable
                                                     update-token
                                                     (:Name variable-data)
                                                     (assoc variable-data
                                                            :LongName
                                                            new-long-name))]
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (let [fetched (mdb/get-concept concept-id revision-id)
              metadata (edn/read-string (:metadata fetched))]
          (is (= (:name variable-data) (:name fetched)))
          (is (= new-long-name (:long-name metadata))))))))

(deftest variable-ingest-permissions-test
  (testing "Ingest variable permissions:"
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
          variable-data (variable-util/make-variable)]
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
      (testing "disallowed create responses:"
        (are3 [token expected]
          (let [response (variable-util/create-variable token variable-data)]
            (is (= expected (:status response))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          reg-user-token 401))
      (testing "disallowed update responses:"
        (are3 [token expected]
          (let [update-response (variable-util/update-variable
                                 token
                                 "A-name"
                                 variable-data)]
            (is (= expected (:status update-response))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          reg-user-token 401))
      (testing "allowed responses:"
        (let [create-response (variable-util/create-variable update-token
                                                             variable-data)
              update-response (variable-util/update-variable
                               update-token
                               (:Name variable-data)
                               variable-data)]
          (testing "create variable status"
            (is (= 201 (:status create-response))))
          (testing "update variable status"
            (is (= 200 (:status update-response)))))))))
