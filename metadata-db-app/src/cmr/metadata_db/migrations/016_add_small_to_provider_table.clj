(ns cmr.metadata-db.migrations.016-add-small-to-provider-table
  "Adds small field to provider table and creates the small provider concept tables. The sql must be
  hard coded here so that the small concepts table matches the other concept tables at the time this
  migration was written. Subsequent migrations will modify all of the concept tables in the same way.
  If we were to just reference out to the current code to create the concept tables it would create
  the tables differently depending on what changes had been made. This would cause subsequent
  migrations to fail."
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]))

(def create-collection-sql
  "CREATE TABLE small_prov_collections (
    id NUMBER,
    concept_id VARCHAR(255) NOT NULL,
    native_id VARCHAR(1030) NOT NULL,
    metadata BLOB NOT NULL,
    format VARCHAR(255) NOT NULL,
    revision_id INTEGER DEFAULT 1 NOT NULL,
    revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    deleted INTEGER DEFAULT 0 NOT NULL,
    short_name VARCHAR(85) NOT NULL,
    version_id VARCHAR(80),
    entry_id VARCHAR(255) NOT NULL,
    entry_title VARCHAR(1030) NOT NULL,
    delete_time TIMESTAMP WITH TIME ZONE,
    provider_id VARCHAR(255) NOT NULL,
    CONSTRAINT small_prov_collections_pk PRIMARY KEY (id),
    CONSTRAINT small_prov_collections_con_rev
      UNIQUE (provider_id, native_id, revision_id)
      USING INDEX (create unique index small_prov_collections_ucr_i
                          ON small_prov_collections(provider_id, native_id, revision_id)),
    CONSTRAINT small_prov_collections_cid_rev
      UNIQUE (concept_id, revision_id)
      USING INDEX (create unique index small_prov_collections_cri
                          ON small_prov_collections(concept_id, revision_id)))")


(def create-granule-sql
  "CREATE TABLE small_prov_granules (id NUMBER,
     concept_id VARCHAR(255) NOT NULL,
     native_id VARCHAR(250) NOT NULL,
     parent_collection_id VARCHAR(255) NOT NULL,
     metadata BLOB NOT NULL,
     format VARCHAR(255) NOT NULL,
     revision_id INTEGER DEFAULT 1 NOT NULL,
     revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
     deleted INTEGER DEFAULT 0 NOT NULL,
     delete_time TIMESTAMP WITH TIME ZONE,
     granule_ur VARCHAR(250),
     provider_id VARCHAR(255) NOT NULL,
     CONSTRAINT small_prov_granules_pk PRIMARY KEY (id),
     CONSTRAINT small_prov_granules_con_rev
      UNIQUE (provider_id, native_id, revision_id)
      USING INDEX (create unique index small_prov_granules_ucr_i
                          ON small_prov_granules (provider_id, native_id, revision_id)),
    CONSTRAINT small_prov_granules_cid_rev
      UNIQUE (concept_id, revision_id)
      USING INDEX (create unique index small_prov_granules_cri
                          ON small_prov_granules (concept_id, revision_id)))")

(def create-coll-seq-sql "CREATE SEQUENCE small_prov_collections_seq")

(def create-gran-seq-sql "CREATE SEQUENCE small_prov_granules_seq")


(defn up
  "Migrates the database up to version 16."
  []
  (println "cmr.metadata-db.migrations.016-add-small-to-provider-table up...")
  (h/sql "alter table providers add small INTEGER DEFAULT 0 NOT NULL")
  ;; Create the SMALL_PROV tables and sequence
  (h/sql create-collection-sql)
  (h/sql create-coll-seq-sql)
  (h/sql create-granule-sql)
  (h/sql create-gran-seq-sql))


(defn down
  "Migrates the database down from version 16."
  []
  (println "cmr.metadata-db.migrations.016-add-small-to-provider-table down...")
  (h/sql "alter table providers drop column small")
  (h/sql "drop table small_prov_collections")
  (h/sql "drop sequence small_prov_collections_seq")
  (h/sql "drop table small_prov_granules")
  (h/sql "drop sequence small_prov_granules_seq"))

