(ns migrations.009-create-concept-id-seq
  (:require [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.concepts :as concepts]))

(defn up
  "Migrates the database up to version 9."
  []
  (println "migrations.009-create-concept-id-seq up...")
  (when (h/concept-id-seq-missing?)
    (h/sql
      (format "CREATE SEQUENCE METADATA_DB.concept_id_seq START WITH %s INCREMENT BY 1 CACHE 20"
              concepts/INITIAL_CONCEPT_NUM))))

(defn down
  "Migrates the database down from version 9."
  []
  (println "migrations.009-create-concept-id-seq down...")
  (println "`down` does nothing for this migration."))