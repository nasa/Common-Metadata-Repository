(ns cmr.metadata-db.migrations.072-add-cmr-sub-notifications-table
  "Add a table for time of last notification to join on subscription table"
  (:require
   [config.mdb-migrate-helper :as h]))

(def ^:private sub-notifications-column-sql
 "id NUMBER,
 subscription_concept_id VARCHAR(255) NOT NULL,
 last_notified_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL")

 (def ^:private sub-notifications-constraint-sql
   (str "CONSTRAINT sub_notifications_pk PRIMARY KEY (id),"
   "CONSTRAINT sub_cid UNIQUE (subscription_concept_id)"))

(defn up
  "Migrates the database up to version 72."
  []
  (println "cmr.metadata-db.migrations.072-add-cmr-sub-notifications-table up...")
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_sub_notifications (%s, %s)"
   sub-notifications-column-sql
   sub-notifications-constraint-sql))
  (h/sql "CREATE SEQUENCE cmr_sub_notifications_seq"))

(defn down
  "Migrates the database down from version 72."
  []
  (println "cmr.metadata-db.migrations.072-add-cmr-sub-notifications-table down.")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_sub_notifications_seq")
  (h/sql "DROP TABLE cmr_sub_notifications"))
