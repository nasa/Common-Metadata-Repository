(ns cmr.metadata-db.migrations.027-revert-entry-ids
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 27. Need to fix migration 26 changes to SIT and WL."
  []
  (println "cmr.metadata-db.migrations.027-revert-entry-ids up...")
  (doseq [t (h/get-collection-tablenames)]
    (println (str "Table: " t))
    (h/sql (format "update %s set entry_id = short_name || '_' || version_id where version_id != 'Not provided'" t))))


(defn down
  "Migrates the database down from version 27."
  []
  (println "cmr.metadata-db.migrations.027-revert-entry-ids down...")
  (throw (Exception. "This migration does not support 'down'")))