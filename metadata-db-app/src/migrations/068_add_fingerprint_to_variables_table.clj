(ns migrations.068-add-fingerprint-to-variables-table
  "Adds fingerprint to variables table"
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 68."
  []
  (println "migrations.068-add-fingerprint-to-variables-table up...")
  (h/sql "alter table cmr_variables add fingerprint VARCHAR(64)")
  (h/sql "CREATE INDEX variables_FPI ON cmr_variables(fingerprint)"))

(defn down
  "Migrates the database down from version 68."
  []
  (println "migrations.068-add-fingerprint-to-variables-table down.")
  (h/sql "alter table cmr_variables drop column fingerprint"))
