(ns cmr.metadata-db.migrations.047-remove-sync-work-tables
  "Removes obsolete sync_work and sync_delete_work tables."
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 47."
  []
  (println "cmr.metadata-db.migrations.047-remove-sync-work-tables up...")
  (h/sql "drop table sync_work")
  (h/sql "drop table sync_delete_work"))

(defn down
  "Migrates the database down from version 47."
  []
  (println "cmr.metadata-db.migrations.047-remove-sync-work-tables down...")
  (h/sql "CREATE TABLE METADATA_DB.sync_work (
         id NUMBER NOT NULL,
         concept_id VARCHAR(255) NOT NULL,
         revision_id NUMBER,
         primary key (id),
         constraint unique_concept_id unique (concept_id))")
  (h/sql "CREATE TABLE METADATA_DB.sync_delete_work (
         concept_id VARCHAR(255) NOT NULL,
         revision_id NUMBER,
         deleted NUMBER)")
  (h/sql "CREATE INDEX sdw_cid_rid ON sync_delete_work(concept_id, revision_id)")
  (h/sql "CREATE INDEX sdw_deleted ON sync_delete_work(deleted)"))
