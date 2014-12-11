(ns cmr.search.data.sids-retriever
  "This is a temporary namespace that provides access to the security identifiers (sids) of an ECHO
  token as stored in the ECHO database. This was added as a temporary workaround to performance
  problems retrieving sids from ECHO. See Jira issues CMR-1126 and ECHO-180. CMR-1128 was added
  to remove this temporary fix."
  (:require [cmr.common.config :as cfg]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.oracle.sql-utils :as sql-utils]
            [cmr.oracle.connection :as oracle]
            [clj-time.core :as t]
            [cmr.metadata-db.data.memory-db]))

(def business-user (cfg/config-value-fn :echo-business-user "DEV_52_BUSINESS"))

(defn- context->db
  "Returns the metadata db database instance."
  [context]
  (get-in context [:system :metadata-db :db]))

(defn- in-the-future?
  "Returns true if the time is in the future."
  [datetime]
  (t/after? datetime (t/now)))

(defprotocol SidsInformation
  "Defines a protocol for fetching security identifier information from the database."
  (get-security-token
    [db token]
    "Returns a map of :is-guest? and :user-guid if the token exists and is valid. Returns nil otherwise.")
  (get-group-guids
    [db user-guid]
    "Returns group guids a user has joined."))

;; Extends the protocol to the OracleStore.
(extend-protocol SidsInformation
  cmr.oracle.connection.OracleStore

  (get-security-token
    [db token]
    (j/with-db-transaction
      [conn db]
      (let [sql (format "select guest, user_guid, act_as_user_guid, expires, revoked
                        from %s.security_token where token = ?"
                        (business-user))
            stmt [sql token]]
        (when-let [{:keys [guest user_guid act_as_user_guid expires revoked]}
                   (first (sql-utils/query conn stmt))]
          (when (and (or (nil? expires)
                         (in-the-future? (oracle/oracle-timestamp->clj-time conn expires)))
                     (nil? revoked))
            {:is-guest? (= (long guest) 1)
             ;; Use the act as user guid if it's set
             :user-guid (or act_as_user_guid user_guid)})))))

  (get-group-guids
    [db user-guid]
    (let [sql (format "select group_guid from %s.GROUP2_MEMBER where USER_GUID = ?" (business-user))
          stmt [sql user-guid]]
      (mapv :group_guid (sql-utils/query db stmt)))))

;; Extends the protocol to the in memory database. We will always return nil when using this database
(extend-protocol SidsInformation
  cmr.metadata_db.data.memory_db.MemoryDB
  (get-security-token [db token] nil)
  (get-group-guids [db user-guid] nil))

(defn get-sids
  "Fetches the security identifiers from a token if it exists, is not revoked, and hasn't expired.
  Sids are returned as as list of strings and keywords. The sids :guest and :registered are special
  and used to signify a guest or registered user. Nil is returned if the token is not valid. It's
  expected that the caller will send a request to the kernel if this function returns nil for
  proper handling of invalid or unknown tokens."
  [context token]
  (let [start (System/currentTimeMillis)
        db (context->db context)
        sids (when-let [{:keys [is-guest? user-guid]} (get-security-token db token)]
               (if is-guest?
                 [:guest]
                 (cons :registered (get-group-guids db user-guid))))]
    (info "Fetched sids from business schema in" (- (System/currentTimeMillis) start) "ms")
    sids))
