(ns migrations.020-rename-tags-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 20."
  []
  (println "migrations.020-rename-tags-table up...")
  (h/sql "RENAME tags_seq TO CMR_tags_seq")
  (h/sql "ALTER TABLE tags RENAME TO CMR_tags"))

(defn down
  "Migrates the database down from version 20."
  []
  (println "migrations.020-rename-tags-table down...")
  (h/sql "RENAME CMR_tags_seq TO tags_seq")
  (h/sql "ALTER TABLE CMR_tags RENAME TO tags"))