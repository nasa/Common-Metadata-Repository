(ns cmr.metadata-db.migrations.069-setup-subscriptions-table
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(def ^:private subscriptions-column-sql
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1030) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  description VARCHAR(255) NOT NULL,
  user_id VARCHAR(30) NOT NULL,
  email_address VARCHAR(255) NOT NULL,
  collection_concept_id VARCHAR(255) NOT NULL,
  transaction_id INTEGER DEFAULT 0 NOT NULL")

(def ^:private subscriptions-constraint-sql
  (str "CONSTRAINT subscriptions_pk PRIMARY KEY (id), "

       ;; Unique constraint on native id and revision id
       "CONSTRAINT subscriptions_nid_rid UNIQUE (native_id, revision_id)
       USING INDEX (create unique index subscriptions_unri ON cmr_subscriptions (native_id, revision_id)), "

       ;; Unique constraint on concept id and revision id
       "CONSTRAINT subscriptions_cid_rid UNIQUE (concept_id, revision_id)
       USING INDEX (create unique index subscriptions_ucri ON cmr_subscriptions (concept_id, revision_id))"))

(defn- create-subscriptions-table
  []
  (h/sql (format "CREATE TABLE METADATA_DB.cmr_subscriptions (%s, %s)" subscriptions-column-sql subscriptions-constraint-sql)))

(defn- create-subscriptions-indices
  []
  (h/sql "CREATE INDEX subscriptions_crdi ON cmr_subscriptions (concept_id, revision_id, deleted)"))

(defn- create-subscriptions-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_subscriptions_seq"))

(defn up
  "Migrates the database up to version 69."
  []
  (println "cmr.metadata-db.migrations.069-setup-subscriptions-table up...")
  (create-subscriptions-table)
  (create-subscriptions-indices)
  (create-subscriptions-sequence))

(defn down
  "Migrates the database down from version 69."
  []
  (println "cmr.metadata-db.migrations.069-setup-subscription-table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_subscriptions_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_subscriptions"))
