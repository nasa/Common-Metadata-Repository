(ns cmr.metadata-db.data.oracle.concept-tables
  (require [cmr.common.services.errors :as errors]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.common.util :as cutil]
           [clojure.string :as string]
           [clojure.pprint :refer (pprint pp)]
           [clojure.java.jdbc :as j]
           [inflections.core :as inf]
           [cmr.metadata-db.data.providers :as p]
           [cmr.metadata-db.services.provider-service :as provider-service]
           [cmr.metadata-db.data.oracle.collection-table :as ct]
           [cmr.metadata-db.data.oracle.granule-table :as gt]))

(def all-concept-types [:collection :granule])

(defn get-table-name
  "Get the name for the table for a given provider and concept-type"
  [provider concept-type]
  ;; Dont' remove the next line - needed to prevent SQL injection
  (provider-service/validate-provider provider)
  (let [{:keys [provider-id small]} provider
        db-provider-id (if small p/small-provider-id provider-id)]
    (format "%s_%s" (string/lower-case db-provider-id) (inf/plural (name concept-type)))))

(defn create-concept-table-id-sequence
  "Create a sequence to populate the ids for a concept table."
  [db provider concept-type]
  (let [sequence-name (str (get-table-name provider concept-type) "_seq")]
    (info "Creating sequence [" sequence-name "]")
    (j/db-do-commands db (format "CREATE SEQUENCE %s" sequence-name))))

(defmulti create-concept-table
  "Create a table to hold concepts of a given type."
  :concept-type)

(defmethod create-concept-table :collection
  [{:keys [db provider]}]
  (let [table-name (get-table-name provider :collection)
        {:keys [small]} provider]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (ct/get-coll-column-sql {:small small
                                                          :table-name table-name})
                                 (ct/get-coll-constraint-sql {:small small
                                                              :table-name table-name})))
    (ct/create-coll-indexes {:small small
                             :db db
                             :table-name table-name})))

(defmethod create-concept-table :granule
  [{:keys [db provider]}]
  (let [table-name (get-table-name provider :granule)
        {:keys [small]} provider]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (gt/get-gran-column-sql {:small small
                                                          :table-name table-name})
                                 (gt/get-gran-constraint-sql {:small small
                                                              :table-name table-name})))
    (gt/create-gran-indexes {:small small
                             :db db
                             :table-name table-name})))

(defn create-provider-concept-tables
  "Create all the concept tables for the given provider."
  [db provider]
  (info "Creating concept tables for provider [" (:provider-id provider) "]")
  (doseq [concept-type all-concept-types]
    (create-concept-table {:db db :provider provider :concept-type concept-type})
    (create-concept-table-id-sequence db provider concept-type)))

(defn delete-provider-concept-tables
  "Delete the concept tables associated with the given provider."
  [db provider]
  (info "Deleting concept tables for provider [" (:provider-id provider) "]")
  (doseq [concept-type all-concept-types]
    (let [table-name (get-table-name provider concept-type)
          sequence-name (str table-name "_seq")]
      (info "Dropping table" table-name)
      (j/db-do-commands db (str "DROP TABLE " table-name))
      (info "Dropping sequence" sequence-name)
      (j/db-do-commands db (str "DROP SEQUENCE " sequence-name)))))
