(ns cmr.metadata-db.data.oracle.granule-table
  "Contains helper functions to create granule table."
  (require [clojure.java.jdbc :as j]))

(defmulti get-gran-column-sql
  (fn [m]
    (:small m)))

(defmethod get-gran-column-sql false
  [m]
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
       "granule_ur VARCHAR(250)"))

(defmethod get-gran-column-sql true
  [m]
  ;; For small provider granule table, there is an extra provider_id column
  (str (get-gran-column-sql {:small false})
       ",provider_id VARCHAR(255) NOT NULL"))

(defmulti get-gran-constraint-sql
  (fn [m]
    (:small m)))

(defmethod get-gran-constraint-sql false
  [{:keys [table-name]}]
  (format (str "CONSTRAINT %s_pk PRIMARY KEY (id), "

               ;; Unique constraint on native id and revision id
               "CONSTRAINT %s_con_rev
               UNIQUE (native_id, revision_id)
               USING INDEX (create unique index %s_ucr_i
               ON %s (native_id, revision_id)), "

               ;; Unique constraint on concept id and revision id
               "CONSTRAINT %s_cid_rev
               UNIQUE (concept_id, revision_id)
               USING INDEX (create unique index %s_cri
               ON %s (concept_id, revision_id))")
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name))

(defmethod get-gran-constraint-sql true
  [{:keys [table-name]}]
  (format (str "CONSTRAINT %s_pk PRIMARY KEY (id), "

               ;; Unique constraint on native id and revision id
               "CONSTRAINT %s_con_rev
               UNIQUE (provider_id, native_id, revision_id)
               USING INDEX (create unique index %s_ucr_i
               ON %s (provider_id, native_id, revision_id)), "

               ;; Unique constraint on concept id and revision id
               "CONSTRAINT %s_cid_rev
               UNIQUE (concept_id, revision_id)
               USING INDEX (create unique index %s_cri
               ON %s (concept_id, revision_id))")
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name))

(defn- create-common-gran-indexes
  [{:keys [db table-name]}]
  ;; can't create constraint with column of datatype TIME/TIMESTAMP WITH TIME ZONE
  ;; so we create the index separately from the create table statement
  (j/db-do-commands db (format "CREATE INDEX %s_crddr ON %s (concept_id, revision_id, deleted, delete_time, revision_date)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_pcid ON %s (parent_collection_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_pcr ON %s (parent_collection_id, concept_id, revision_id)"
                               table-name
                               table-name))

  ;; This index is needed when bulk indexing granules within a collection
  (j/db-do-commands db (format "CREATE INDEX %s_cpk ON %s (parent_collection_id, id)"
                               table-name
                               table-name))

  ;; This index makes it faster to find counts by collection of undeleted granules for metadata db holdings
  (j/db-do-commands
    db (format "create index %s_pdcr on %s (parent_collection_id, deleted, concept_id, revision_id)"
               table-name
               table-name)))

(defmulti create-gran-indexes
  (fn [m]
    (:small m)))

(defmethod create-gran-indexes false
  [{:keys [db table-name]}]
  (create-common-gran-indexes {:db db :table-name table-name})
  (j/db-do-commands db (format "CREATE INDEX idx_%s_ur ON %s(granule_ur)" table-name table-name)))

(defmethod create-gran-indexes true
  [{:keys [db table-name]}]
  (create-common-gran-indexes {:db db :table-name table-name})
  (j/db-do-commands db (format "CREATE INDEX idx_%s_pur ON %s(provider_id, granule_ur)"
                               table-name table-name)))

