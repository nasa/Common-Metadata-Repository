(ns migrations.023-remove-small-provider-services-user-id-not-null-constraint
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-helper :as h]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 23."
  []
  (println "migrations.023-remove-small-provider-services-user-id-not-null-constraint up...")
  (h/sql "alter table small_prov_services modify user_id varchar(30)"))

(defn down
  "Migrates the database down from version 23."
  []
  (println "migrations.023-remove-small-provider-services-user-id-not-null-constraint down...")
  (h/sql "alter table small_prov services modify user_id varchar(30) not null"))