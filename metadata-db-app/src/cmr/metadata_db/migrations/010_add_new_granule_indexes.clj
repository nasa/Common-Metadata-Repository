(ns cmr.metadata-db.migrations.010-add-new-granule-indexes
  "Adds additional index"
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 10."
  []
  (println "cmr.metadata-db.migrations.010-add-new-granule-indexes up...")
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "create index %s_pdcr on %s (parent_collection_id, deleted, concept_id, revision_id)"
                   t t))))

(defn down
  "Migrates the database down from version 10."
  []
  (println "cmr.metadata-db.migrations.010-add-new-granule-indexes down.")
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "drop index %s_pdcr" t))))
