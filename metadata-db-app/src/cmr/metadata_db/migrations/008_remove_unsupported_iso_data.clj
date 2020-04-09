(ns cmr.metadata-db.migrations.008-remove-unsupported-iso-data
  "Bootstrap database migrations originally copied everything from Catalog REST. There is certain
  ISO types we do not support from Catalog REST. This database migration removes that data from the
  Metadata DB. The formats removed are ISO - the prototype iso format and ISO-GRACE which has two
  strings because Catalog REST changed it at one point. We could never index this data so unindexing
  it is unnecessary."
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 8."
  []
  (println "cmr.metadata-db.migrations.008-remove-unsupported-iso-data up...")
  (doseq [t (h/get-regular-provider-collection-tablenames)]
    (h/sql (format "delete from %s where format in ('ISO', 'GRACE_ISO', 'ISO-GRACE')" t)))
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "delete from %s where format in ('ISO', 'GRACE_ISO', 'ISO-GRACE')" t))))

(defn down
  "Migrates the database down from version 8."
  []
  (println "cmr.metadata-db.migrations.008-remove-unsupported-iso-data down. Does nothing."))
