(ns migrations.048-add-created-at-to-collection-tables
  "Adds created_at column to collection tables."
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 48."
  []
  (println "migrations.048-add-created-at-to-collection-tables up...")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s add created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL" t))
    (h/sql (format "CREATE INDEX %s_c_i ON %s(created_at)" t t))))

(defn down
  "Migrates the database down from version 48."
  []
  (println "migrations.048-add-created-at-to-collection-tables down...")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s drop column created_at" t))))
