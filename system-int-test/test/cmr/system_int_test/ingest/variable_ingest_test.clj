(ns cmr.system-int-test.ingest.variable-ingest-test
  "CMR variable ingest integration tests."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.variable-util :as variable-util]))

(use-fixtures :each (ingest-util/reset-fixture))

(deftest create-variable-ingest-test
  (testing "Variable ingest:"
    (let [acl-data (variable-util/setup-update-acl (s/context))
          {:keys [user-name group-name group-id token grant-id]} acl-data
          variable-data (variable-util/make-variable)
          response (variable-util/create-variable token variable-data)
          {:keys [status concept-id revision-id]} response
          fetched (mdb/get-concept concept-id revision-id)
          metadata (edn/read-string (:metadata fetched))]
      (testing "create a new variable"
        ;; Sanity check on tokens; we've had issues with ACLs, so the following is
        ;; handy when first testing ingest. More complete ACL testing is done
        ;; below as well as in other sys-int test namespaces (e.g., associations).
        (is (e/permitted? token
                          grant-id
                          group-id
                          (ingest-util/get-ingest-update-acls token)))
        (is (= 201 status))
        (is (= 1 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= user-name (:user-id fetched)))
        (is (= user-name (:originator-id metadata)))
        (is (= (string/lower-case (:Name variable-data)) (:native-id fetched)))
        (is (= (:LongName variable-data) (:long-name metadata)))
        (variable-util/assert-variable-saved variable-data
                                             user-name
                                             concept-id
                                             revision-id))
      (testing "attempt to create a new variable when it already exists"
        (let [response (variable-util/create-variable token variable-data)
              {:keys [status errors]} response]
          (is (= 409 status))
          (is (= (format (str "A variable with native-id '%s' already "
                              "exists with concept id '%s'.")
                         (:native-id fetched)
                         concept-id)
                 (first errors))))))))

(deftest update-variable-ingest-test
  (testing "Variable ingest:"
    (let [acl-data (variable-util/setup-update-acl (s/context))
          {:keys [user-name group-name group-id token grant-id]} acl-data
          variable-data (variable-util/make-variable)
          new-long-name "A new long name"
          _ (variable-util/create-variable token variable-data)
          response (variable-util/update-variable
                    token
                    (string/lower-case (:Name variable-data))
                    (assoc variable-data :LongName new-long-name))
          {:keys [status concept-id revision-id]} response
          fetched (mdb/get-concept concept-id revision-id)
          metadata (edn/read-string (:metadata fetched))]
      (testing "update an existing variable"
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= (string/lower-case (:Name variable-data)) (:native-id fetched)))
        (is (= new-long-name (:long-name metadata))))
      (testing "attempt to update a variable that doesn't exist"
        (let [response (variable-util/update-variable
                        token
                        "i-don't-exist"
                        (assoc variable-data :LongName new-long-name))
              {:keys [status errors]} response]
          (is (= 404 status))
          (is (= "Variable could not be found with variable-name 'i-don't-exist'"
                 (first errors))))))))

(deftest delete-variable-ingest-test
  (testing "Variable ingest:"
    (let [acl-data (variable-util/setup-update-acl (s/context))
          {:keys [user-name group-name group-id token grant-id]} acl-data
          variable-data (variable-util/make-variable)
          new-long-name "A new long name"
          _ (variable-util/create-variable token variable-data)
          response (variable-util/delete-variable
                    token
                    (string/lower-case (:Name variable-data)))
          {:keys [status concept-id revision-id]} response
          fetched (mdb/get-concept concept-id revision-id)]
      (testing "delete a variable"
        (is (= 200 status))
        (is (= 2 revision-id))
        (is (:deleted fetched))
        (is (= (string/lower-case (:Name variable-data))
               (:native-id fetched)))
        (variable-util/assert-variable-deleted variable-data
                                               user-name
                                               concept-id
                                               revision-id))
      (testing "attempt to update a deleted variable"
        (let [response (variable-util/update-variable
                        token
                        (string/lower-case (:Name variable-data))
                        (assoc variable-data :LongName new-long-name))
              {:keys [status errors]} response]
          (is (= 404 status))
          (is (= (format "Variable with variable-name '%s' was deleted."
                         (string/lower-case (:Name variable-data)))
                 (first errors)))))
      (testing "create a variable over a variable's tombstone"
        (let [response (variable-util/create-variable
                        token
                        variable-data)
              {:keys [status concept-id revision-id]} response]
          (is (= 200 status))
          (is (= 3 revision-id)))))))

(deftest variable-ingest-permissions-test
  (testing "Variable ingest permissions:"
    (let [;; Groups
          guest-group-id (e/get-or-create-group
                          (s/context) "umm-var-guid1")
          reg-user-group-id (e/get-or-create-group
                             (s/context) "umm-var-guid2")
          ;; Tokens
          guest-token (e/login
                       (s/context) "umm-var-user1" [guest-group-id])
          reg-user-token (e/login
                          (s/context) "umm-var-user2" [reg-user-group-id])
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
          {update-user-name :user-name
           update-group-name :group-name
           update-token :token
           update-grant-id :grant-id
           update-group-id :group-id} (variable-util/setup-update-acl (s/context))
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
                                 (string/lower-case (:Name variable-data))
                                 variable-data)]
            (is (= expected (:status update-response))))
          "no token provided"
          nil 401
          "guest user denied"
          guest-token 401
          "regular user denied"
          reg-user-token 401))
       (testing "disallowed delete responses:"
        (are3 [token expected]
          (let [update-response (variable-util/delete-variable
                                 token
                                 (string/lower-case (:Name variable-data)))]
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
                               (string/lower-case (:Name variable-data))
                               variable-data)
              delete-response (variable-util/delete-variable
                               update-token
                               (string/lower-case (:Name variable-data)))]
          (testing "create variable status"
            (is (= 201 (:status create-response))))
          (testing "update variable status"
            (is (= 200 (:status update-response))))
          (testing "update variable status"
            (is (= 200 (:status delete-response)))))))))
