(ns cmr.access-control.int-test.access-control-test-util
  (:require [cmr.transmit.access-control :as ac]
            [clojure.test :as ct :refer [is]]
            [cmr.transmit.config :as config]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.access-control.system :as system]
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

(defn create-mdb-system
  "Creates an in memory version of metadata db."
  ([]
   (create-mdb-system false))
  ([use-external-db]
   (let [mq (mem-queue/create-memory-queue-broker (mdb-config/rabbit-mq-config))
         mdb-sys (mdb-system/create-system)]
     (merge mdb-sys
            {:queue-broker mq
             :scheduler (jobs/create-non-running-scheduler)}
            (when-not use-external-db
              {:db (memory/create-db)})))))

(defn int-test-fixtures
  "Returns test fixtures for starting the access control application and its external dependencies."
  []
  (ct/join-fixtures
   [(common-client-test-util/run-app-fixture
     conn-context
     :access-control
     (system/create-system)
     system/start
     system/stop)

    (common-client-test-util/run-app-fixture
     conn-context
     :echo-rest
     (mock-echo-system/create-system)
     mock-echo-system/start
     mock-echo-system/stop)

    (common-client-test-util/run-app-fixture
     conn-context
     :metadata-db
     (create-mdb-system)
     mdb-system/start
     mdb-system/stop)]))

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
  "Creates A test fixture that grants all users the ability to create and modify groups for the given providers"
  [f]
  ;; TODO CMR-2133, CMR-2134 update this when implementing ACLS
  ; (e/grant-all-group (conn-context))
  (f))

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
  [concept-id]
  (process-response (ac/get-group (conn-context) concept-id {:raw? true})))

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
  [params]
  (process-response (ac/search-for-groups (conn-context) params {:raw? true})))

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
           (dissoc concept :revision-date)))))

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
           (dissoc concept :revision-date)))))
