(ns cmr.metadata-db.data.oracle.concept-tables
  (require [cmr.common.services.errors :as errors]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.common.util :as cutil]
           [clojure.string :as string]
           [clojure.pprint :refer (pprint pp)]
           [clojure.java.jdbc :as j]
           [cmr.metadata-db.services.util :as util]
           [inflections.core :as inf]))

(def all-concept-types [:collection :granule])

(defn get-table-name
  "Get the name for the table for a given provider-id and concept-type"
  [provider-id concept-type]
  ;; Dont' remove the next line - needed to prevent SQL injection
  (util/validate-provider-id provider-id)
  (format "%s_%s" (string/lower-case provider-id) (inf/plural (name concept-type))))

(defmulti create-concept-table
  "Create a table to hold concepts of a given type."
  :concept-type)

(defmethod create-concept-table :collection [{:keys [db provider-id]}]
  (let [table-name (get-table-name provider-id :collection)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (
                                 concept_id VARCHAR(255) NOT NULL,
                                 native_id VARCHAR(1030) NOT NULL,
                                 metadata BLOB NOT NULL,
                                 format VARCHAR(255) NOT NULL,
                                 revision_id INTEGER DEFAULT 0 NOT NULL,
                                 revision_date VARCHAR(30) DEFAULT TO_CHAR(SYSDATE, 'YYYY-MM-DD hh:mm:ss') NOT NULL,
                                 deleted INTEGER DEFAULT 0 NOT NULL,
                                 short_name VARCHAR(85) NOT NULL,
                                 version_id VARCHAR(80) NOT NULL,
                                 entry_title VARCHAR(1030) NOT NULL,
                                 CONSTRAINT %s_con_rev
                                 UNIQUE (native_id, revision_id)
                                 USING INDEX (create unique index %s_ucr_i on
                                 %s(native_id, revision_id)),
                                 CONSTRAINT %s_cid_rev
                                 UNIQUE (concept_id, revision_id)
                                 USING INDEX (create unique index %s_cri
                                 ON %s(concept_id, revision_id)))"
                                 table-name
                                 table-name
                                 table-name
                                 table-name
                                 table-name
                                 table-name
                                 table-name))
     (j/db-do-commands db (format "CREATE INDEX %s_snv_i ON %s(short_name, version_id) TABLESPACE users"
                                 table-name
                                 table-name))
     (j/db-do-commands db (format "CREATE INDEX %s_et_i ON %s(entry_title) TABLESPACE users"
                                 table-name
                                 table-name))))

(defmethod create-concept-table :granule [{:keys [db provider-id]}]
  (let [table-name (get-table-name provider-id :granule)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (
                                 concept_id VARCHAR(255) NOT NULL,
                                 native_id VARCHAR(250) NOT NULL,
                                 parent_collection_id VARCHAR(255) NOT NULL,
                                 metadata BLOB NOT NULL,
                                 format VARCHAR(255) NOT NULL,
                                 revision_id INTEGER DEFAULT 0 NOT NULL,
                                 revision_date VARCHAR(30) DEFAULT TO_CHAR(SYSDATE, 'YYYY-MM-DD hh:mm:ss') NOT NULL,
                                 deleted INTEGER DEFAULT 0 NOT NULL,
                                 CONSTRAINT %s_con_rev
                                 UNIQUE (native_id, revision_id)
                                 USING INDEX (create unique index %s_ucr_i on
                                 %s(native_id, revision_id)),
                                 CONSTRAINT %s_cid_rev
                                 UNIQUE (concept_id, revision_id)
                                 USING INDEX (create unique index %s_cri
                                 ON %s(concept_id, revision_id)))"
                                 table-name
                                 table-name
                                 table-name
                                 table-name
                                 table-name
                                 table-name
                                 table-name))))

(defn create-provider-concept-tables
  "Create all the concept tables for the given provider-id."
  [db provider-id]
  (info "Creating concept tables for provider [" provider-id "]")
  (doseq [concept-type all-concept-types]
    (create-concept-table {:db db :provider-id provider-id :concept-type concept-type})))

(defn delete-provider-concept-tables
  "Delete the concept tables associated with the given provider-id."
  [db provider-id]
  (info "Deleting concept tables for provider [" provider-id "]")
  (doseq [concept-type all-concept-types]
    (j/db-do-commands db (str "DROP TABLE " (get-table-name provider-id concept-type)))))
