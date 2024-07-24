(ns cmr.metadata-db.data.oracle.granule-table
  "Contains helper functions to create granule table."
  (:require
   [clojure.java.jdbc :as jdbc]
   [cmr.metadata-db.data.util :as util]))

(def create-granule-column-sql
  (str "id NUMBER,
       concept_id VARCHAR(255) NOT NULL,
       native_id VARCHAR(250) NOT NULL,
       parent_collection_id VARCHAR(255) NOT NULL,
       metadata BLOB NOT NULL,
       format VARCHAR(255) NOT NULL,
       revision_id INTEGER DEFAULT 1 NOT NULL,
       revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
       deleted INTEGER DEFAULT 0 NOT NULL,
       delete_time TIMESTAMP WITH TIME ZONE,"

       ;; Note that the granule_ur column allows NULL because we do not
       ;; populate the granule_ur column as part of the initial
       ;; migration. We should change the column to NOT NULL once it is
       ;; fully populated.
       "granule_ur VARCHAR(250),
        transaction_id INTEGER DEFAULT 0 NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL"))

(defn granule-column-sql
  "Returns the sql to define provider granule columns"
  [provider]
  (case (:small provider)
    false create-granule-column-sql
    true (str create-granule-column-sql ",provider_id VARCHAR(255) NOT NULL")))

(defn granule-constraint-sql
  "Returns the sql to define constraint on provider granule table"
  [provider table-name]
  (util/validate-table-name table-name)
  (let [native-revision-str-1 (format "CONSTRAINT %s_pk PRIMARY KEY (id), CONSTRAINT %s_con_rev " table-name table-name)
        native-revision-2 (if (:small provider)
                            (format (str "UNIQUE (provider_id, native_id, revision_id) USING INDEX (create unique index %s_ucr_i "
                                         "ON %s (provider_id, native_id, revision_id)), ")
                                    table-name
                                    table-name)
                            (format (str "UNIQUE (native_id, revision_id) USING INDEX (create unique index %s_ucr_i "
                                         "ON %s (native_id, revision_id)), ")
                                    table-name
                                    table-name))
        con-rev-constraint (format (str "CONSTRAINT %s_cid_rev UNIQUE (concept_id, revision_id) "
                                        "USING INDEX (create unique index %s_cri ON %s (concept_id, revision_id))")
                                   table-name
                                   table-name
                                   table-name)]
    (str native-revision-str-1 native-revision-2 con-rev-constraint)))

(defn- create-common-gran-indexes
  [db table-name]
  ;; can't create constraint with column of datatype TIME/TIMESTAMP WITH TIME ZONE
  ;; so we create the index separately from the create table statement
  (util/validate-table-name table-name)
  (jdbc/db-do-commands db (format "CREATE INDEX %s_crddr ON %s (concept_id, revision_id, deleted, delete_time, revision_date)"
                                  table-name
                                  table-name))
  (jdbc/db-do-commands db (format "CREATE INDEX %s_pcid ON %s (parent_collection_id)"
                                  table-name
                                  table-name))
  (jdbc/db-do-commands db (format "CREATE INDEX %s_pcr ON %s (parent_collection_id, concept_id, revision_id)"
                                  table-name
                                  table-name))
  ;; Need this for transaction-id post commit check
  (jdbc/db-do-commands db (format "CREATE INDEX %s_crtid ON %s (concept_id, revision_id, transaction_id)"
                                  table-name
                                  table-name))
  ;; This index is needed when bulk indexing granules within a collection
  (jdbc/db-do-commands db (format "CREATE INDEX %s_cpk ON %s (parent_collection_id, id)"
                                  table-name
                                  table-name))
  ;; This index makes it faster to find counts by collection of undeleted granules for metadata db holdings
  (jdbc/db-do-commands db (format "create index %s_pdcr on %s (parent_collection_id, deleted, concept_id, revision_id)"
                                  table-name
                                  table-name)))

(defn create-granule-indexes
  "Create indexes on provider granule table"
  [db provider table-name]
  (util/validate-table-name table-name)
  (create-common-gran-indexes db table-name)
  (case (:small provider)
    false (do
            (jdbc/db-do-commands db (format "CREATE INDEX idx_%s_ur ON %s(granule_ur)" table-name table-name))
            ;; This index makes it much faster to find granules that are not deleted that have a delete time.
            (jdbc/db-do-commands db (format "create index %s_dd on %s (deleted, delete_time)" table-name table-name))
            (jdbc/db-do-commands db (format "create index %s_dr on %s (deleted, revision_date)" table-name table-name)))
    true (do
           (jdbc/db-do-commands db (format "CREATE INDEX idx_%s_pur ON %s(provider_id, granule_ur)" table-name table-name))
           ;; This index makes it much faster to find granules that are not deleted that have a delete time.
           (jdbc/db-do-commands db (format "create index %s_dd on %s (provider_id, deleted, delete_time)" table-name table-name))
           (jdbc/db-do-commands db (format "create index %s_dr on %s (provider_id, deleted, revision_date)" table-name table-name)))))
