(ns cmr.system-int-test.ingest.variable-ingest-test
  "CMR collection ingest integration tests"
  (:require
    [clj-http.client :as client]
    [clj-time.core :as t]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.access-control.test.util :as ac]
    [cmr.acl.core :as acl]
    [cmr.common-app.test.side-api :as side]
    [cmr.common.date-time-parser :as p]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :as util]
    [cmr.common.util :refer [are3]]
    [cmr.ingest.config :as config]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-variable :as data-umm-var]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.transmit.config :as transmit-config]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.test.expected-conversion :as exc]
    [cmr.umm-spec.test.location-keywords-helper :as lkt]
    [cmr.umm-spec.umm-spec-core :as umm-spec]))

(use-fixtures :each (ingest/reset-fixture))

(defn- ingest-succeeded?
  "Returns true if the provided token has permission to perform the given
  function."
  [response]
  (let [status (:status response)]
    (is (some #{status} [200 201 204 401 404]))
    (not= status 401)))

(defn- get-acls
  "Get a token's management ACLs."
  [token]
  (-> (s/context)
      (assoc :token token)
      (acl/get-permitting-acls :system-object
                               e/ingest-management-acl
                               :update)))

(defn- grant-permitted?
  "Check if a given grant id is in the list of provided ACLs."
  [grant-id acls]
  (contains?
    (into
      #{}
      (map :guid acls))
    grant-id))

(defn- group-permitted?
  "Check if a given group id is in the list of provided ACLs."
  [group-id acls]
  (contains?
    (reduce
      #(into %1 (map :group-guid %2))
      #{}
      (map :aces acls))
    group-id))

(defn- permitted?
  "Check if a the ACLs for the given token include the given grant and group
  IDs."
  [token grant-id group-id]
  (let [acls (get-acls token)]
    (and (grant-permitted? grant-id acls)
         (group-permitted? group-id acls))))

(defn- not-permitted?
  [& args]
  (not (apply permitted? args)))

;; Verify the UMM-Var is ingested successfully.
(deftest variable-ingest-test
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
          variable data-umm-var/simple-json-variable]
      (is (permitted? update-token update-grant-id
                      update-group-id))
      (let [{:keys [concept-id revision-id]
             :as response} (ingest/ingest-variable
                            variable
                            {:accept-format :json
                             :token update-token})]
        (is (ingest-succeeded? response))
        (index/wait-until-indexed)
        (is (mdb/concept-exists-in-mdb? concept-id revision-id))
        (is (= 1 revision-id))))))

(deftest variable-ingest-permissions-test
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
          variable data-umm-var/simple-json-variable]
    (testing "acl setup and grants for different users"
      (is (not-permitted? guest-token guest-grant-id
                          guest-group-id))
      (is (not-permitted? reg-user-token reg-user-grant-id
                          reg-user-group-id))
      (is (permitted? update-token update-grant-id
                      update-group-id)))
    (testing "ingest variable creation permissions"
      (are3 [token expected]
        (let [response (ingest/ingest-variable
                        variable
                        {:accept-format :json
                         :token token
                         :allow-failure? true})]
          (is (= expected (:status response))))
        "System update permission allowed"
        update-token 201
        "Regular user denied"
        reg-user-token 401
        "Guest user denied"
        guest-token 401)))))
