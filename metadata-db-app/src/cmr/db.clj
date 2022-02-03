(ns cmr.db
  "Entry point for the db related operations. Defines a main method that accepts arguments."
  (:require
   [clojure.java.jdbc :as j]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.sql-utils :as su]
   [cmr.oracle.user :as o]
   [config.mdb-migrate-config :as mc]
   [drift.execute :as drift])
  (:gen-class))

(defconfig echo-business-user
  "echo business user"
  {:default "DEV_52_BUSINESS"})

(def create-security-token-table-sql
  "Create security token table in ECHO business schema"
  (format "CREATE TABLE %s.SECURITY_TOKEN (
          token VARCHAR2(200 CHAR),
          guest NUMBER(10),
          guid VARCHAR2(50 CHAR),
          user_guid VARCHAR2(50 CHAR),
          act_as_user_guid VARCHAR2(50 CHAR),
          expires TIMESTAMP(6),
          revoked TIMESTAMP(6))"
          (echo-business-user)))

(def create-group2-member-table-sql
  "Create group2_member table in ECHO business schema"
  (format "CREATE TABLE %s.GROUP2_MEMBER (
          user_guid VARCHAR2(50 CHAR),
          group_guid VARCHAR2(50 CHAR))"
          (echo-business-user)))

(defn- create-echo-business-schema
  "Creates the minimum business-schema setup in order to support metadata-db accesses. Only the
  tables actually accessed are created and those tables only have the columns used by metadata-db.
  Throws an exception if creation fails."
  [db]
  (su/ignore-already-exists-errors "ECHO business user"
                                   (o/create-user db (echo-business-user) (echo-business-user)))
  (su/ignore-already-exists-errors "ECHO business schema SECURITY_TOKEN table"
                                   (j/db-do-commands db create-security-token-table-sql))
  (su/ignore-already-exists-errors "ECHO business schema GROUP2_MEMBER table"
                                   (j/db-do-commands db create-group2-member-table-sql)))

(defn create-user
  []
  (let [db (oracle-config/sys-dba-db-spec)
        catalog-rest-user (mdb-config/catalog-rest-db-username)
        metadata-db-user (mdb-config/metadata-db-username)
        metadata-db-password (mdb-config/metadata-db-password)]
    (su/ignore-already-exists-errors "METADATA_DB user"
                                     (o/create-user db metadata-db-user
                                                    metadata-db-password))
    ;; Metadata DB needs access to the catalog-rest database tables for the DB synchronization task.
    ;; It also needed access for the initial migration of data from ECHO to CMR.
    (su/ignore-already-exists-errors "Catalog rest user"
                                     (o/create-user db catalog-rest-user catalog-rest-user))
    (o/grant-select-privileges db catalog-rest-user metadata-db-user)

    ;; Due to poor performance from the ECHO ACL endpoint we directly access the ECHO business
    ;; schema. For initial setup the database tables may or may not exist already. We create the
    ;; tables as needed for CMR if they do not exist.
    (create-echo-business-schema db)
    (o/grant-select-privileges db (echo-business-user) metadata-db-user)

    ;; Allow database synchronization tests to create and drop tables in the Catalog REST database.
    (o/grant-create-drop-any-table-privileges db metadata-db-user)))

(defn drop-user
  []
  (let [db (oracle-config/sys-dba-db-spec)
        metadata-db-user (mdb-config/metadata-db-username)]
    (o/drop-user db metadata-db-user)))

(defn -main
  "Execute the given database operation specified by input arguments."
  [& args]
  (info "Running " args)
  (let [op (first args)]
    (try
      (cond
        (= "create-user" op)
        (create-user)

        (= "drop-user" op)
        (drop-user)

        (= "migrate" op)
        (drift/run
          (conj
           (vec args)
           "-c"
           "config.mdb_migrate_config/app-migrate-config"))

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (shutdown-agents)
        (System/exit 1))))

  (shutdown-agents)
  (System/exit 0))
