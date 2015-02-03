(ns migrations.011-add-entry-id-to-collection-tables
  "Adds entry id to collection tables"
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 11."
  []
  (println "migrations.011-add-entry-id-to-collection-tables up...")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s add entry_id VARCHAR(255)" t))
    (h/sql (format "update %s set entry_id = concat(concat(short_name, '_'), version_id)" t))
    (h/sql (format "alter table %s modify entry_id VARCHAR(255) NOT NULL" t))))

(defn down
  "Migrates the database down from version 11."
  []
  (println "migrations.011-add-entry-id-to-collection-tables down.")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s drop column entry_id" t))))

