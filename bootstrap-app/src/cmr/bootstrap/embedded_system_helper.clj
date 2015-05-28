(ns cmr.bootstrap.embedded-system-helper
  "Contains general helper functions")

(defn get-metadata-db
  "Returns the embedded metadata-db from the given bootstrap system"
  [system]
  (get-in system [:embedded-systems :metadata-db]))

(defn get-metadata-db-db
  "Returns the db instance of the metadata-db from the given system"
  [system]
  (get-in system [:embedded-systems :metadata-db :db]))

(defn get-indexer
  "Returns the embedded indexer from the given bootstrap system"
  [system]
  (get-in system [:embedded-systems :indexer]))