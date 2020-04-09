(ns cmr.metadata-db.migrations.020-rename-tags-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 20."
  []
  (println "cmr.metadata-db.migrations.020-rename-tags-table up...")
  (h/sql "RENAME tags_seq TO cmr_tags_seq")
  (h/sql "ALTER TABLE tags RENAME TO cmr_tags"))

(defn down
  "Migrates the database down from version 20."
  []
  (println "cmr.metadata-db.migrations.020-rename-tags-table down...")
  (h/sql "RENAME cmr_tags_seq TO tags_seq")
  (h/sql "ALTER TABLE cmr_tags RENAME TO tags"))