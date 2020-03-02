(ns cmr.metadata-db.migrations.026-fix-entry-ids
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 26."
  []
  (println "cmr.metadata-db.migrations.026-fix-entry-ids up...")
  (doseq [t (h/get-collection-tablenames)]
    (println (str "Table: " t))

    ;; Set version id to a default value.
    (h/sql (format "update %s set version_id = 'Not provided' where version_id is null" t))

    ;; Version id does not allow null values anymore
    (h/sql (format "alter table %s modify (VERSION_ID not null)" t))

    (h/sql (format "update %s set entry_id = short_name where version_id = 'Not provided'" t))
    (h/sql (format "update %s set entry_id = short_name || '_' || version_id where version_id != 'Not provided'" t))))


(defn down
  "Migrates the database down from version 26."
  []
  (println "cmr.metadata-db.migrations.026-fix-entry-ids down...")
  (throw (Exception. "This migration does not support 'down'")))
