(ns cmr.metadata-db.migrations.017-add-short-name-to-provider-table
  "Adds short name to providers table"
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 17."
  []
  (println "cmr.metadata-db.migrations.017-add-short-name-to-provider-table up...")
  (h/sql "alter table providers add short_name VARCHAR(128)")
  (h/sql "update providers set short_name = provider_id")
  (h/sql "alter table providers modify short_name VARCHAR(128) NOT NULL")
  (h/sql "create unique index provider_sn_index on providers(short_name)"))

(defn down
  "Migrates the database down from version 17."
  []
  (println "cmr.metadata-db.migrations.017-add-short-name-to-provider-table down...")
  (h/sql "alter table providers drop column short_name"))
