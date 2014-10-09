(ns migrations.006-remove-not-null-version-id-constraint
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 6."
  []
  (println "migrations.006-remove-not-null-version-id-constraint up...")
  (doseq [coll-table (h/get-collection-tablenames)]
    (h/sql (format "alter table %s modify (VERSION_ID null)" coll-table))))

(defn down
  "Migrates the database down from version 6."
  []
  (println "migrations.006-remove-not-null-version-id-constraint down...")
  (doseq [coll-table (h/get-collection-tablenames)]
    (h/sql (format "alter table %s modify (VERSION_ID not null)" coll-table))))

