(ns cmr.bootstrap.migrations.002-update-quartz-to-2-2-2
  (:require [clojure.java.jdbc :as j]
            [config.bootstrap-migrate-config :as config]))

(defn up
  "Migrates the database up to version 2. Adds a column needed for Quartz 2.2.0 and higher."
  []
  (println "migrations.002-update-quartz-to-2-2-2 up...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE QRTZ_FIRED_TRIGGERS ADD SCHED_TIME NUMBER(13) DEFAULT 0 NOT NULL"))

(defn down
  "Migrates the database down from version 2."
  []
  (println "migrations.002-update-quartz-to-2-2-2 down...")
  (j/db-do-commands (config/db) "ALTER TABLE QRTZ_FIRED_TRIGGERS DROP COLUMN SCHED_TIME"))
