(ns migrations.008-create-concept-id-seq
  (:require [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.concepts :as concepts]))

(defn up
  "Migrates the database up to version 8."
  []
  (println "migrations.008-create-concept-id-seq up...")
  (try
    (when (h/concept-id-seq-missing?)
      (h/sql
        (format "CREATE SEQUENCE METADATA_DB.concept_id_seq START WITH %s INCREMENT BY 1 CACHE 20"
                concepts/INITIAL_CONCEPT_NUM)))
    (catch Exception e)))

(defn down
  "Migrates the database down from version 8."
  []
  (println "migrations.008-create-concept-id-seq down...")
  (println "`down` does nothing for this migration."))