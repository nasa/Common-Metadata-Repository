(ns migrations.019-add-user-id-to-small-collection-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 19."
  []
  (println "migrations.019-add-user-id-to-small-collection-table up...")
  (h/sql "alter table small_prov_collections add user_id VARCHAR(200)")
  (h/sql "CREATE INDEX small_prov_collections_uid_i ON small_prov_collections(user_id)"))

(defn down
  "Migrates the database down from version 19."
  []
  (println "migrations.019-add-user-id-to-small-collection-table down...")
  (h/sql "alter table small_prov_collections drop column user_id"))