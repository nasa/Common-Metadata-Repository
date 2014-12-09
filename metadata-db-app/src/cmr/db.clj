(ns cmr.db
  "Entry point for the db related operations. Defines a main method that accepts arguments."
  (:require [cmr.common.log :refer (debug info warn error)]
            [drift.execute :as drift]
            [cmr.oracle.user :as o]
            [cmr.oracle.config :as oracle-config]
            [cmr.metadata-db.config :as mdb-config]
            [config.migrate-config :as mc])
  (:gen-class))

(defn create-user
  []
  (let [db (oracle-config/sys-dba-db-spec)
        catalog-rest-user (mdb-config/catalog-rest-db-username)
        metadata-db-user (mdb-config/db-username)]
    (o/create-user db metadata-db-user (mdb-config/db-password))
    (o/grant-select-privileges db catalog-rest-user metadata-db-user)

    ;; This is done to allow bootstrap tests to create and drop test tables in the Catalog REST
    ;; database schema.
    (o/grant-create-drop-any-table-privileges db metadata-db-user)))

(defn drop-user
  []
  (let [db (oracle-config/sys-dba-db-spec)
        metadata-db-user (mdb-config/db-username)]
    (o/drop-user db metadata-db-user)))

(defn -main
  "Execute the given database operation specified by input arguments."
  [& args]
  (info "Running " args)

  ;; Set up db connection first before running migration
  ;; to work around the transient connection issue as documented in CMR-1108
  (mc/db)

  (let [op (first args)]
    (try
      (cond
        (= "create-user" op)
        (create-user)

        (= "drop-user" op)
        (drop-user)

        (= "migrate" op)
        (drift/run args)

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (System/exit 1))))

  (System/exit 0))
