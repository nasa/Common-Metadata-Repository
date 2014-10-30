(ns migrations.007-create-sync-work-table
  "Creates a table used by bootstrap to perform synchronization between the Catalog REST database
  and Metadata DB"
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  []
  (println "migrations.007-create-sync-work-table up...")
  (h/sql "CREATE TABLE METADATA_DB.sync_work (
         id NUMBER NOT NULL,
         concept_id VARCHAR(255) NOT NULL,
         primary key (id),
         constraint unique_concept_id unique (concept_id))"))

(defn down
  []
  (println "migrations.007-create-sync-work-table down...")
  (h/sql "DROP TABLE METADATA_DB.sync_work"))