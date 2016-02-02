(ns cmr.metadata-db.data.oracle.service-table
  "Contains helper functions to create services tables."
  (require [clojure.java.jdbc :as j]))

(defmulti service-column-sql
  "Returns the sql to define provider service columns"
  (fn [provider]
    (:small provider)))

(defmethod service-column-sql false
  [provider]
  "id NUMBER,
  concept_id VARCHAR(255) NOT NULL,
  native_id VARCHAR(1030) NOT NULL,
  metadata BLOB NOT NULL,
  format VARCHAR(255) NOT NULL,
  revision_id INTEGER DEFAULT 1 NOT NULL,
  revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  deleted INTEGER DEFAULT 0 NOT NULL,
  entry_id VARCHAR(255) NOT NULL,
  entry_title VARCHAR(1030) NOT NULL,
  delete_time TIMESTAMP WITH TIME ZONE,
  user_id VARCHAR(30),
  transaction_id INTEGER DEFAULT 0 NOT NULL")

(defmethod service-column-sql true
  [provider]
  ;; For small provider service table, there is an extra provider_id column
  (str (service-column-sql {:small false})
       ",provider_id VARCHAR(255) NOT NULL"))

(defmulti service-constraint-sql
  "Returns the sql to define constraint on provider service table"
  (fn [provider table-name]
    (:small provider)))

(defmethod service-constraint-sql false
  [provider table-name]
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

(defmethod service-constraint-sql true
  [provider table-name]
  (format (str "CONSTRAINT %s_pk PRIMARY KEY (id), "

            ;; Unique constraint on provider id, native id and revision id
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

(defmulti create-service-indexes
  "Create indexes on provider service table"
  (fn [db provider table-name]
    (:small provider)))

(defmethod create-service-indexes false
  [db _ table-name]
  (j/db-do-commands db (format "CREATE INDEX %s_crdi ON %s (concept_id, revision_id, deleted, delete_time)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_eid_i ON %s (entry_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_et_i ON %s (entry_title)"
                               table-name
                               table-name))
  ;; Need this for transaction-id post commit check
  (j/db-do-commands db (format "CREATE INDEX %s_crtid ON %s (concept_id, revision_id, transaction_id)"
                                table-name
                                table-name)))

(defmethod create-service-indexes true
  [db _ table-name]
  (j/db-do-commands db (format "CREATE INDEX %s_crdi ON %s (concept_id, revision_id, deleted, delete_time)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_p_ed_i ON %s (provider_id, entry_id)"
                               table-name
                               table-name))
  (j/db-do-commands db (format "CREATE INDEX %s_p_et_i ON %s (provider_id, entry_title)"
                               table-name
                               table-name))
  ;; Need this for transaction-id post commit check
  (j/db-do-commands db (format "CREATE INDEX %s_crtid ON %s (concept_id, revision_id, transaction_id)"
                                table-name
                                table-name)))
