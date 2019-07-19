(ns cmr.metadata-db.migrations.007-create-sync-work-table
  "Creates a table used by bootstrap to perform synchronization between the Catalog REST database
  and Metadata DB"
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  []
  (println "cmr.metadata-db.migrations.007-create-sync-work-table up...")
  (h/sql "CREATE TABLE METADATA_DB.sync_work (
         id NUMBER NOT NULL,
         concept_id VARCHAR(255) NOT NULL,
         revision_id NUMBER,
         primary key (id),
         constraint unique_concept_id unique (concept_id))")

  (h/sql "CREATE TABLE METADATA_DB.sync_delete_work (
         concept_id VARCHAR(255) NOT NULL,
         revision_id NUMBER,
         deleted NUMBER)"))

(defn down
  []
  (println "cmr.metadata-db.migrations.007-create-sync-work-table down...")
  (h/sql "DROP TABLE METADATA_DB.sync_work")
  (h/sql "DROP TABLE METADATA_DB.sync_delete_work"))