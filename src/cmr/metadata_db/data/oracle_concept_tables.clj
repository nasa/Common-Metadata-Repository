(ns cmr.metadata-db.data.oracle-concept-tables
  (require [cmr.common.services.errors :as errors]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.common.util :as cutil]
           [clojure.string :as string]
           [clojure.pprint :refer (pprint pp)]
           [clojure.java.jdbc :as j]
           [cmr.metadata-db.services.utility :as util]))

(def all-concept-types [:collection :granule])

(defn delete-provider-concept-tables
  "Delete the concept tables associated with the given provider-id."
  [db provider-id]
  (for [concept-type all-concept-types]
    (let [table-name (format "%s_%s" (string/lower-case provider-id) (name concept-type))]
      (j/db-do-commands db (str "DROP TABLE METADATA_DB." table-name)))))

(defmulti create-concept-table 
  "Create a table to hold concepts of a given type."
  :concept-type)

(defmethod create-concept-table :collection [db provider_id]
 
  (j/db-do-commands db (format "CREATE TABLE METADATA_DB.concept (
                                      concept_type VARCHAR(255) NOT NULL,
                                      native_id VARCHAR(255) NOT NULL,
                                      concept_id VARCHAR(255) NOT NULL,
                                      provider_id VARCHAR(255) NOT NULL,
                                      metadata VARCHAR(4000) NOT NULL,
                                      format VARCHAR(255) NOT NULL,
                                      revision_id INTEGER DEFAULT 0 NOT NULL,
                                      deleted INTEGER DEFAULT 0 NOT NULL,
                                      CONSTRAINT unique_concept_revision 
                                      UNIQUE (concept_type, provider_id, native_id, revision_id)
                                      USING INDEX (create unique index ucr_index on 
                                      concept(concept_type, provider_id, native_id, revision_id)),
                                      CONSTRAINT unique_concept_id_revision
                                      UNIQUE (concept_id, revision_id)
                                      USING INDEX (create unique index cid_rev_indx 
                                      ON concept(concept_id, revision_id)))")))
