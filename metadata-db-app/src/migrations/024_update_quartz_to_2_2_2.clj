(ns migrations.024-update-quartz-to-2-2-2
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 24. Adds a column needed for Quartz 2.2.0 and higher."
  []
  (println "migrations.024-update-quartz-to-2-2-2 up...")
  ;; Alter table does not work directly for changing from varchar to BLOB,
  ;; so we drop the table and recreate it with the new type since we don't need to preserve the data.
  ;; We're storing the provider acl hash as a compressed value.
  (j/db-do-commands (config/db)
                    "ALTER TABLE QRTZ_FIRED_TRIGGERS ADD SCHED_TIME NUMBER(13) DEFAULT 0 NOT NULL"))

(defn down
  "Migrates the database down from version 24."
  []
  (println "migrations.024-update-quartz-to-2-2-2 down...")
  (j/db-do-commands (config/db) "ALTER TABLE QRTZ_FIRED_TRIGGERS DROP COLUMN SCHED_TIME"))