(ns migrations.018-add-user-id-collection-tables
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 18."
  []
  (println "migrations.018-add-user-id-collection-tables up...")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s add user_id VARCHAR(200)" t))
    (h/sql (format "CREATE INDEX %s_uid_i ON %s(user_id)" t t))))

(defn down
  "Migrates the database down from version 18."
  []
  (println "migrations.018-add-user-id-collection-tables down...")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s drop column user_id" t))))





