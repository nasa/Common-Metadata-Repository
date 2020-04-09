(ns cmr.metadata-db.migrations.023-remove-small-provider-services-user-id-not-null-constraint
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-helper :as h]
            [config.mdb-migrate-config :as config]))

(defn up
  "Migrates the database up to version 23."
  []
  (println "cmr.metadata-db.migrations.023-remove-small-provider-services-user-id-not-null-constraint up...")
  (h/sql "alter table small_prov_services modify user_id null"))

(defn down
  "Migrates the database down from version 23."
  []
  (println "cmr.metadata-db.migrations.023-remove-small-provider-services-user-id-not-null-constraint down...")
  (h/sql "alter table small_prov_services modify user_id not null"))