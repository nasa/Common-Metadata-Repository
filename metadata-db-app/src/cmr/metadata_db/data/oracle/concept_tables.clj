(ns cmr.metadata-db.data.oracle.concept-tables
  (:require
   [clojure.java.jdbc :as j]
   [clojure.pprint :refer (pprint pp)]
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.concepts :as cc]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.oracle.collection-table :as ct]
   [cmr.metadata-db.data.oracle.granule-table :as gt]
   [cmr.metadata-db.services.provider-validation :as pv]
   [inflections.core :as inf]))

(def all-provider-concept-types
  "All the concept types that have tables for each (non-small) provider"
  [:collection :granule])

(defmulti get-table-name
  "Get the name for the table for a given provider and concept-type"
  (fn [provider-id concept-type]
    concept-type))

(defmethod get-table-name :access-group
  [_ _]
  "cmr_groups")

(defmethod get-table-name :acl
  [_ _]
  "cmr_acls")

(defmethod get-table-name :tag
  [_ _]
  "cmr_tags")

(defmethod get-table-name :tag-association
  [_ _]
  "cmr_associations")

(defmethod get-table-name :humanizer
  [_ _]
  "cmr_humanizers")

(defmethod get-table-name :subscription
  [_ _]
  "cmr_subscriptions")

(defmethod get-table-name :service
  [_ _]
  "cmr_services")

(defmethod get-table-name :tool
  [_ _]
  "cmr_tools")

(defmethod get-table-name :variable
  [_ _]
  "cmr_variables")

(defmethod get-table-name :variable-association
  [_ _]
  "cmr_associations")

(defmethod get-table-name :service-association
  [_ _]
  "cmr_associations")

(defmethod get-table-name :tool-association
  [_ _]
  "cmr_associations")

(defmethod get-table-name :generic-association
  [_ _]
  "cmr_associations")

(doseq [concept-type (cc/get-generic-concept-types-array)]
  (defmethod get-table-name concept-type
    [_ _]
    "cmr_generic_documents"))

(defmethod get-table-name :default
  [provider concept-type]
  ;; Don't remove the next line - needed to prevent SQL injection
  (pv/validate-provider provider)
  (let [{:keys [provider-id small]} provider
        db-provider-id (if small pv/small-provider-id provider-id)]
    (format "%s_%s" (string/lower-case db-provider-id) (inf/plural (name concept-type)))))

(defn create-concept-table-id-sequence
  "Create a sequence to populate the ids for a concept table."
  [db provider concept-type]
  (let [sequence-name (str (get-table-name provider concept-type) "_seq")]
    (info "Creating sequence [" sequence-name "]")
    (j/db-do-commands db (format "CREATE SEQUENCE %s" sequence-name))))

(defmulti create-concept-table
  "Create a table to hold concepts of a given type."
  (fn [db provider concept-type]
    concept-type))

(defmethod create-concept-table :collection
  [db provider concept-type]
  (let [table-name (get-table-name provider :collection)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (ct/collection-column-sql provider)
                                 (ct/collection-constraint-sql provider table-name)))
    (ct/create-collection-indexes db provider table-name)))

(defmethod create-concept-table :granule
  [db provider concept-type]
  (let [table-name (get-table-name provider :granule)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (gt/granule-column-sql provider)
                                 (gt/granule-constraint-sql provider table-name)))
    (gt/create-granule-indexes db provider table-name)))

(defn create-provider-concept-tables
  "Create all the concept tables for the given provider."
  [db provider]
  (info "Creating concept tables for provider [" (:provider-id provider) "]")
  (doseq [concept-type all-provider-concept-types]
    (create-concept-table db provider concept-type)
    (create-concept-table-id-sequence db provider concept-type)))

(defn delete-provider-concept-tables
  "Delete the concept tables associated with the given provider."
  [db provider]
  (info "Deleting concept tables for provider [" (:provider-id provider) "]")
  (doseq [concept-type all-provider-concept-types]
    (let [table-name (get-table-name provider concept-type)
          sequence-name (str table-name "_seq")]
      ;; there is a chance that when switching from in-memory database to sql
      ;; database that only one of these table are created causing errors when
      ;; a test provider is being reset. Each drop is independent now so that
      ;; cmr can be started while in a bad state.
      (try
        (do
          (info "Dropping table" table-name)
          (j/db-do-commands db (str "DROP TABLE " table-name)))
        (catch java.sql.BatchUpdateException bue
          (warn "Could not drop table [" table-name "] because " (.getMessage bue))))

      (try
        (do
          (info "Dropping sequence" sequence-name)
          (j/db-do-commands db (str "DROP SEQUENCE " sequence-name)))
        (catch java.sql.BatchUpdateException bue
          (warn "Could not drop table [" table-name "] because " (.getMessage bue)))))))
