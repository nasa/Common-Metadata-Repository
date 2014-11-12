(ns config.mdb-migrate-helper
  "Contains helper functions for performing database migrations"
  (:require [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.oracle.concept-tables :as concept-tables]
            [config.migrate-config :as config]))

(defn sql
  "Applies the sql update"
  [stmt-str]
  (println "Applying update" stmt-str)
  (j/db-do-commands (config/db) stmt-str))

(defn get-provider-ids
  "Gets a list of the provider ids in the database. Primarily for enabling migrations of existing
  provider tables."
  []
  (map :provider_id (j/query (config/db) "select provider_id from metadata_db.providers")))

(defn get-collection-tablenames
  "Gets a list of all the collection tablenames. Primarily for enabling migrations of existing
  provider tables."
  []
  (map #(concept-tables/get-table-name % :collection) (get-provider-ids)))

(defn get-granule-tablenames
  "Gets a list of all the granule tablenames. Primarily for enabling migrations of existing
  provider tables."
  []
  (map #(concept-tables/get-table-name % :granule) (get-provider-ids)))

(defn concept-id-seq-missing?
  "Returns true if concept_id_seq does not exist"
  []
  (let [result (j/query
                 (config/db)
                 "select count(*) as c from all_sequences where sequence_name='CONCEPT_ID_SEQ'")
        seq-count (:c (first result))]
    (= 0 (int seq-count))))

