(ns cmr.access-control.test.util
  (:require [cmr.transmit.access-control :as ac]
            [clojure.test :as ct :refer [is]]
            [clj-http.client :as client]
            [cmr.transmit.config :as config]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.access-control.system :as system]
            [cmr.access-control.config :as access-control-config]
            [cmr.elastic-utils.test-util :as elastic-test-util]
            [cmr.elastic-utils.config :as es-config]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.mock-echo.system :as mock-echo-system]
            [cmr.mock-echo.client.mock-echo-client :as mock-echo-client]
            [cmr.mock-echo.client.mock-urs-client :as mock-urs-client]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.common-app.test.client-util :as common-client-test-util]
            [cmr.common.mime-types :as mt]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.metadata-db.config :as mdb-config]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.message-queue.queue.memory-queue :as mem-queue]
            [cmr.message-queue.config :as rmq-conf]
            [cmr.message-queue.test.queue-broker-wrapper :as queue-broker-wrapper]
            [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
            [cmr.common.jobs :as jobs]))

(def conn-context-atom
  "An atom containing the cached connection context map."
  (atom nil))

(defn conn-context
  "Retrieves a context map that contains a connection to the access control app."
  []
  (when-not @conn-context-atom
    (reset! conn-context-atom {:system (config/system-with-connections
                                         {}
                                         [:access-control :echo-rest :metadata-db :urs])}))
  @conn-context-atom)

(defn queue-config
  []
  (rmq-conf/merge-configs (mdb-config/rabbit-mq-config)
                          (access-control-config/rabbit-mq-config)))

(defn create-memory-queue-broker
  []
  (mem-queue/create-memory-queue-broker (queue-config)))

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
  (let [queue-broker (queue-broker-wrapper/create-queue-broker-wrapper (create-memory-queue-broker))]
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
     (ac/reset (conn-context))
     (doseq [[provider-guid provider-id] provider-map]
       (mdb/create-provider (assoc (conn-context) :token (config/echo-system-token))
                            {:provider-id provider-id}))
     (e/create-providers (conn-context) provider-map)

     (when (seq usernames)
      (mock-urs-client/create-users (conn-context) (for [username usernames]
                                                     {:username username
                                                      :password (str username "pass")})))

     ;; TODO Temporarily granting all admin. Remove this when implementing  CMR-2133, CMR-2134
     (e/grant-all-admin (conn-context))

     (f))))

(defn grant-all-group-fixture
  "Returns a test fixture function which grants all users the ability to create and modify groups for given provider guids."
  [provider-guids]
  (fn [f]
    (e/grant-system-group-permissions-to-all (conn-context))
    (doseq [provider-guid provider-guids]
      (e/grant-provider-group-permissions-to-all (conn-context) provider-guid))
    (f)))

(defn refresh-elastic-index
  []
  (client/post (format "http://localhost:%s/_refresh" (es-config/elastic-port))))

(defn wait-until-indexed
  "Waits until all messages are processed and then flushes the elasticsearch index"
  []
  (qb-side-api/wait-for-terminal-states)
  (refresh-elastic-index))

(defn make-group
  "Makes a valid group"
  ([]
   (make-group nil))
  ([attributes]
   (merge {:name "Administrators"
           :description "A very good group"}
          attributes)))

(defn- process-response
  "Takes an HTTP response that may have a parsed body. If the body was parsed into a JSON map then it
  will associate the status with the body otherwise returns a map of the unparsed body and status code."
  [{:keys [status body]}]
  (if (map? body)
    (assoc body :status status)
    {:status status
     :body body}))

(defn create-group
  "Creates a group."
  ([token group]
   (create-group token group nil))
  ([token group options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/create-group (conn-context) group options)))))

(defn get-group
  "Retrieves a group by concept id"
  [token concept-id]
  (process-response (ac/get-group (conn-context) concept-id {:raw? true :token token})))

(defn update-group
  "Updates a group."
  ([token concept-id group]
   (update-group token concept-id group nil))
  ([token concept-id group options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/update-group (conn-context) concept-id group options)))))

(defn delete-group
  "Deletes a group"
  ([token concept-id]
   (delete-group token concept-id nil))
  ([token concept-id options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (ac/delete-group (conn-context) concept-id options)))))

(defn search
  "Searches for groups using the given parameters"
  ([token params]
   (search token params nil))
  ([token params options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/search-for-groups (conn-context) params options)))))

(defn add-members
  "Adds members to the group"
  ([token concept-id members]
   (add-members token concept-id members nil))
  ([token concept-id members options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/add-members (conn-context) concept-id members options)))))

(defn remove-members
  "Removes members from the group"
  ([token concept-id members]
   (remove-members token concept-id members nil))
  ([token concept-id members options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/remove-members (conn-context) concept-id members options)))))

(defn get-members
  "Gets members in the group"
  ([token concept-id]
   (get-members token concept-id nil))
  ([token concept-id options]
   (let [options (merge {:raw? true :token token} options)]
    (process-response (ac/get-members (conn-context) concept-id options)))))

(defn create-group-with-members
  "Creates a group with the given list of members."
  ([token group members]
   (create-group-with-members token group members nil))
  ([token group members options]
   (let [group (create-group token group options)]
     (if (seq members)
       (let [{:keys [revision-id status] :as resp} (add-members token (:concept-id group) members options)]
         (when-not (= status 200)
           (throw (Exception. (format "Unexpected status [%s] when adding members: %s" status (pr-str resp)))))
         (assoc group :revision-id revision-id))
       group))))

(defn assert-group-saved
  "Checks that a group was persisted correctly in metadata db. The user-id indicates which user
  updated this revision."
  [group user-id concept-id revision-id]
  (let [concept (mdb/get-concept (conn-context) concept-id revision-id)]
    (is (= {:concept-type :access-group
            :native-id (:name group)
            :provider-id (:provider-id group "CMR")
            :format mt/edn
            :metadata (pr-str group)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

(defn assert-group-deleted
  "Checks that a group tombstone was persisted correctly in metadata db."
  [group user-id concept-id revision-id]
  (let [concept (mdb/get-concept (conn-context) concept-id revision-id)]
    (is (= {:concept-type :access-group
            :native-id (:name group)
            :provider-id (:provider-id group "CMR")
            :metadata ""
            :format mt/edn
            :user-id user-id
            :deleted true
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))
