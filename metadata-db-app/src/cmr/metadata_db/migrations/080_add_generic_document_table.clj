(ns cmr.metadata-db.migrations.080-add-generic-document-table
  (:require
   [config.mdb-migrate-helper :as h]))

(defn- create-generic-documents-table
  []
  (h/sql
   "CREATE TABLE METADATA_DB.cmr_generic_documents (
    id NUMBER,
    concept_id VARCHAR(255) NOT NULL,
    native_id VARCHAR(1030) NOT NULL,
    provider_id VARCHAR(10) NOT NULL,
    document_name VARCHAR(20) NOT NULL,
    schema VARCHAR(255) NOT NULL,
    format VARCHAR(255) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    metadata BLOB NOT NULL,
    revision_id INTEGER DEFAULT 1 NOT NULL,
    revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    deleted INTEGER DEFAULT 0 NOT NULL,
    user_id VARCHAR(30),
    transaction_id INTEGER DEFAULT 0 NOT NULL,

    CONSTRAINT generic_doc_pk PRIMARY KEY (id),

    CONSTRAINT gen_doc_cid_rev UNIQUE (concept_id, revision_id)
    USING INDEX (create unique index generic_cri
    ON cmr_generic_documents (concept_id, revision_id)))"))

(defn- create-generic-document-indices
  []
  ;; Supports queries to find generic documents that have been deleted
  (h/sql
   "CREATE INDEX generic_documents_crdi
    ON METADATA_DB.cmr_generic_documents (concept_id, deleted)")
  
  ;; Supports queries to find specific generic document matching a native id
  ;; and revision id within one provider
  (h/sql
   "CREATE INDEX gen_doc_native_id_rev
    ON METADATA_DB.cmr_generic_documents (provider_id, native_id, revision_id)"))

(defn- create-generic-document-sequence
  []
  (h/sql "CREATE SEQUENCE cmr_generic_documents_seq"))

(defn up
  "Migrate the database up to version 80"
  []
  (println "cmr.metadata-db.migration.080_add_generic_document_table up...")
  (create-generic-documents-table)
  (create-generic-document-indices)
  (create-generic-document-sequence))

(defn down "Migrate the database down from version 80"
  []
  (println "cmr.metadata-db.migration.080_add_generic_document_table down...")
  (h/sql "DROP SEQUENCE METADATA_DB.cmr_generic_documents_seq")
  (h/sql "DROP TABLE METADATA_DB.cmr_generic_documents"))
