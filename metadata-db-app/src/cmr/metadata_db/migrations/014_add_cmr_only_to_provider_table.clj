(ns cmr.metadata-db.migrations.014-add-cmr-only-to-provider-table
  "Adds entry id to collection tables"
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 14."
  []
  (println "cmr.metadata-db.migrations.014-add-cmr-only-to-provider-table up...")
  (h/sql "alter table providers add cmr_only INTEGER DEFAULT 0 NOT NULL"))

(defn down
  "Migrates the database down from version 14."
  []
  (println "cmr.metadata-db.migrations.014-add-cmr-only-to-provider-table down.")
  (h/sql "alter table providers drop column cmr_only"))

