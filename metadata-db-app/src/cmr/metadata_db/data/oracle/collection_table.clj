(ns cmr.metadata-db.data.oracle.collection-table
  "Contains helper functions to create collection table."
  (require [clojure.java.jdbc :as j]))

(defmulti get-coll-column-sql
  (fn [m]
    (:small m)))

(defmethod get-coll-column-sql false
  [m]
  "id NUMBER,
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
  delete_time TIMESTAMP WITH TIME ZONE")

(defmethod get-coll-column-sql true
  [m]
  ;; For small provider collection table, there is an extra provider_id column
  (str (get-coll-column-sql {:small false})
       ",provider_id VARCHAR(255) NOT NULL"))

(defmulti get-coll-constraint-sql
  (fn [m]
    (:small m)))

(defmethod get-coll-constraint-sql false
  [{:keys [table-name]}]
  (format (str "CONSTRAINT %s_pk PRIMARY KEY (id), "

               ;; Unique constraint on native id and revision id
               "CONSTRAINT %s_con_rev
               UNIQUE (native_id, revision_id)
               USING INDEX (create unique index %s_ucr_i
               ON %s(native_id, revision_id)), "

               ;; Unique constraint on concept id and revision id
               "CONSTRAINT %s_cid_rev
               UNIQUE (concept_id, revision_id)
               USING INDEX (create unique index %s_cri
               ON %s(concept_id, revision_id))")
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name))

(defmethod get-coll-constraint-sql true
  [{:keys [table-name]}]
  (format (str "CONSTRAINT %s_pk PRIMARY KEY (id), "

            ;; Unique constraint on native id and revision id
            "CONSTRAINT %s_con_rev
            UNIQUE (provider_id, native_id, revision_id)
            USING INDEX (create unique index %s_ucr_i
            ON %s(provider_id, native_id, revision_id)), "

            ;; Unique constraint on concept id and revision id
            "CONSTRAINT %s_cid_rev
            UNIQUE (concept_id, revision_id)
            USING INDEX (create unique index %s_cri
            ON %s(concept_id, revision_id))")
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name
          table-name))

(defmulti create-coll-indexes
  (fn [m]
    (:small m)))

(defmethod create-coll-indexes false
  [{:keys [db table-name]}]
  (j/db-do-commands db (format "CREATE INDEX %s_crdi ON %s (concept_id, revision_id, deleted, delete_time)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_snv_i ON %s (short_name, version_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_eid_i ON %s (entry_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_et_i ON %s (entry_title)"
                               table-name
                               table-name)))

(defmethod create-coll-indexes true
  [{:keys [db table-name]}]
  (j/db-do-commands db (format "CREATE INDEX %s_crdi ON %s (concept_id, revision_id, deleted, delete_time)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_p_s_i ON %s (provider_id, short_name, version_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_p_ed_i ON %s (provider_id, entry_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_p_et_i ON %s (provider_id, entry_title)"
                               table-name
                               table-name)))
