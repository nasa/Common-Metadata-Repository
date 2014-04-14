(ns cmr.metadata-db.data.oracle.concept-tables
  (require [cmr.common.services.errors :as errors]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.common.util :as cutil]
           [clojure.string :as string]
           [clojure.pprint :refer (pprint pp)]
           [clojure.java.jdbc :as j]
           [cmr.metadata-db.services.utility :as util]
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
    (info "Creating table " table-name)
    (j/db-do-commands db (format "CREATE TABLE %s (
                                 concept_id VARCHAR(255) NOT NULL,
                                 native_id VARCHAR(255) NOT NULL,
                                 metadata VARCHAR(4000) NOT NULL,
                                 format VARCHAR(255) NOT NULL,
                                 revision_id INTEGER DEFAULT 0 NOT NULL,
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

(defmethod create-concept-table :granule [{:keys [db provider-id]}]
  (let [table-name (get-table-name provider-id :granule)]
    (info "Creating table " table-name)
    (j/db-do-commands db (format "CREATE TABLE %s (
                                 concept_id VARCHAR(255) NOT NULL,
                                 native_id VARCHAR(255) NOT NULL,
                                 parent_collection_id VARCHAR(255) NOT NULL,
                                 metadata VARCHAR(4000) NOT NULL,
                                 format VARCHAR(255) NOT NULL,
                                 revision_id INTEGER DEFAULT 0 NOT NULL,
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
  (info "Creating concept tables for provider " provider-id)
  (dorun (for [concept-type all-concept-types]
           (create-concept-table {:db db :provider-id provider-id :concept-type concept-type}))))

(defn delete-provider-concept-tables
  "Delete the concept tables associated with the given provider-id."
  [db provider-id]
  (info "Deleting concept tables for provider " provider-id)
  (dorun (for [concept-type all-concept-types]
           (j/db-do-commands db (str "DROP TABLE " (get-table-name provider-id concept-type))))))
