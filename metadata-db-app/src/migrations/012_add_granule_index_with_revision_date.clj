(ns migrations.012-add-granule-index-with-revision-date
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 12."
  []
  (println "migrations.012-add-granule-index-with-revision-date up...")
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "create index %s_crddr on %s (concept_id, revision_id, deleted, delete_time, revision_date)"))
    (h/sql (format "drop index %s_crdi" t))))

(defn down
  "Migrates the database down from version 12."
  []
  (println "migrations.012-add-granule-index-with-revision-date down...")
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "drop index %s_crddr" t))))