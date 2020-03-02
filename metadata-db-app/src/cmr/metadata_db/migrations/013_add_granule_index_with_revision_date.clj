(ns cmr.metadata-db.migrations.013-add-granule-index-with-revision-date
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  []
  (println "cmr.metadata-db.migrations.013-add-granule-index-with-revision-date up...")
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "create index %s_crddr on %s (concept_id, revision_id, deleted, delete_time, revision_date)" t t))
    (h/sql (format "drop index %s_crdi" t))))

(defn down
  []
  (println "cmr.metadata-db.migrations.013-add-granule-index-with-revision-date down...")
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "create index %s_crdi on %s (concept_id, revision_id, deleted, delete_time)" t t))
    (h/sql (format "drop index %s_crddr" t))))
