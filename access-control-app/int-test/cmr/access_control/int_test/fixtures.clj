(ns cmr.access-control.int-test.fixtures
  (:require
   [clojure.test :as ct]
   [cmr.access-control.config :as access-control-config]
   [cmr.access-control.system :as system]
   [cmr.access-control.test.util :as test-util :refer [conn-context]]
   [cmr.common-app.test.client-util :as common-client-test-util]
   [cmr.common.jobs :as jobs]
   [cmr.elastic-utils.test-util :as elastic-test-util]
   [cmr.message-queue.config :as q-conf]
   [cmr.message-queue.queue.memory-queue :as mem-queue]
   [cmr.message-queue.queue.sqs :as sqs]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.message-queue.test.queue-broker-wrapper :as queue-broker-wrapper]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.data.memory-db :as memory]
   [cmr.metadata-db.system :as mdb-system]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.mock-echo.client.mock-echo-client :as mock-echo-client]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs-client]
   [cmr.mock-echo.system :as mock-echo-system]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db2 :as mdb]))

(defn queue-config
  "Create the message queue configuration needed by access-control."
  []
  (q-conf/merge-configs (mdb-config/queue-config)
                        (access-control-config/queue-config)))

(defn get-broker-backend
  "Create the appropriate broker backend. If an SQS endpoint is defined,
  an SQS broker will be used."
  []
  (let [aws? (= "aws" (q-conf/queue-type))
        cfg (queue-config)]
    (if aws?
      (sqs/create-queue-broker cfg)
      (mem-queue/create-memory-queue-broker cfg))))

(defn create-broker
  "Create the testing broker, wrapping the appropriate backend."
  []
  (queue-broker-wrapper/create-queue-broker-wrapper
   (get-broker-backend)))

(defn create-mdb-system
  "Creates an in memory version of metadata db."
  ([]
   (create-mdb-system false))
  ([use-external-db]
   (let [mdb-sys (mdb-system/create-system)]
     (merge mdb-sys
            {:scheduler (jobs/create-non-running-scheduler)}
            (when-not use-external-db
              {:db (memory/create-db)})))))

(defn int-test-fixtures
  "Returns test fixtures for starting the access control application and its external dependencies.
   The test fixtures only start up applications and side APIs if it detects the applications are not
   already running on the ports requested. This allows the tests to be run in different scenarios
   and still work. The applications may already be running in dev-system or through user.clj If they
   are running these fixtures won't do anything. If it isn't running these fixtures will start up the
   applications and the test will work."
  []
  (let [queue-broker (create-broker)]
    (ct/join-fixtures
      [elastic-test-util/run-elastic-fixture
       (common-client-test-util/run-app-fixture
         conn-context
         :access-control
         (assoc (system/create-system) :queue-broker queue-broker)
         system/start
         system/stop)

       ;; Create a side API that will allow waiting for the queue broker terminal states to be achieved.
       (common-client-test-util/side-api-fixture
         (fn [_]
           (qb-side-api/build-routes queue-broker))
         nil)

       (common-client-test-util/run-app-fixture
         conn-context
         :echo-rest
         (mock-echo-system/create-system)
         mock-echo-system/start
         mock-echo-system/stop)

       (common-client-test-util/run-app-fixture
         conn-context
         :metadata-db
         (assoc (create-mdb-system) :queue-broker queue-broker)
         mdb-system/start
         mdb-system/stop)])))

(defn reset-fixture
  "Test fixture that resets the application before each test and creates providers and users listed.
  provider-map should be a map of provider guids to provider ids. usernames should be a list of usernames
  that exist in URS. The password for each username will be username + \"pass\"."
  ([]
   (reset-fixture nil))
  ([provider-map]
   (reset-fixture provider-map nil))
  ([provider-map usernames]
   (fn [f]
     (mock-echo-client/reset (conn-context))
     (mdb/reset (conn-context))
     (ac/reset (conn-context) {:bootstrap-data? true})
     (e/grant-system-group-permissions-to-admin-group (conn-context) :create :read)
     (doseq [[provider-guid provider-id] provider-map]
       (mdb/create-provider (assoc (conn-context) :token (config/echo-system-token))
                            {:provider-id provider-id})
       ;; Create provider in mock echo with the guid set to the ID to make things easier to sync up
       (e/create-providers (conn-context) {provider-id provider-id})
       ;; Give full permission to the mock admin user to modify groups for the provider
       (e/grant-provider-group-permissions-to-admin-group
        (conn-context) provider-id :create :read))

     (when (seq usernames)
       (mock-urs-client/create-users (conn-context) (for [username usernames]
                                                      {:username username
                                                       :password (str username "pass")})))
     ;; Resetting adds bootstrap minimal data to access control. Wait until it's indexed.
     (test-util/wait-until-indexed)
     (f))))

(defn grant-all-group-fixture
  "Returns a test fixture function which grants all users the ability to create and modify groups
   for given provider guids."
  [provider-guids]
  (fn [f]
    (e/grant-system-group-permissions-to-all (conn-context))
    (doseq [provider-guid provider-guids]
      (e/grant-provider-group-permissions-to-all (conn-context) provider-guid))
    (f)))

(defn grant-admin-group-fixture
  "Returns a test fixture function which grants all users the ability to create and modify groups
   for given provider guids."
  [provider-guids]
  (fn [f]
    (e/grant-system-group-permissions-to-admin-group (conn-context))
    (doseq [provider-guid provider-guids]
      (e/grant-provider-group-permissions-to-admin-group (conn-context) provider-guid))
    (f)))

;;These two vars will be rebinded dynamically when the fixtures are setup for each test and
;;are used to represent the ACLs inside of the tests
(def ^:dynamic *fixture-provider-acl*)
(def ^:dynamic *fixture-system-acl*)

(defn grant-provider-acl-permissions-to-all
  "Creates provider acls granting create on group-id or user-type"
  []
  (let [acl {:group_permissions [{:user_type "registered"
                                  :permissions ["read" "update" "create" "delete"]}
                                 {:user_type "guest"
                                  :permissions ["read" "update" "create" "delete"]}]
              :provider_identity {:provider_id "PROV1"
                                  :target "CATALOG_ITEM_ACL"}}
        {:keys [concept_id revision_id]} (ac/create-acl (merge {:token config/mock-echo-system-token}
                                                               (conn-context))
                                                        acl)]
    (assoc acl :concept-id concept_id :revision-id revision_id)))

(defn grant-any-acl-system-acl-to-all
  "Creates system acl targeting ANY_ACL that grants create, read, update, and delete to all"
  []
  (let [acl {:group_permissions [{:user_type "registered"
                                  :permissions ["read" "update" "create" "delete"]}
                                 {:user_type "guest"
                                  :permissions ["read" "update" "create" "delete"]}]
             :system_identity {:target "ANY_ACL"}}
        {:keys [concept_id revision_id]} (ac/create-acl
                                           (merge {:token config/mock-echo-system-token}
                                                  (conn-context))
                                           acl)]
    (assoc acl :concept-id concept_id :revision-id revision_id)))

(defn grant-all-acl-fixture
  "Returns a test fixture function which grants guest ability to create, read, update, and delete any ACL."
  []
  (fn [f]
    (let [system-acl (grant-any-acl-system-acl-to-all)
          provider-acl (grant-provider-acl-permissions-to-all)]
      (binding [*fixture-system-acl* system-acl
                *fixture-provider-acl* provider-acl]
        (f)))))
