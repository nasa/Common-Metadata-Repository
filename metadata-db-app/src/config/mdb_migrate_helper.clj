(ns config.mdb-migrate-helper
  "Contains helper functions for performing database migrations"
  (:require [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.oracle.concept-tables :as concept-tables]
            [config.migrate-config :as config]
            [cmr.metadata-db.data.oracle.providers]
            [cmr.metadata-db.data.providers :as p]))

(defn sql
  "Applies the sql update"
  [stmt-str]
  (println "Applying" stmt-str)
  (let [start-time (System/currentTimeMillis)]
    (j/db-do-commands (config/db) stmt-str)
    (println (format "Finished %s in [%s] ms."
                     stmt-str
                     (- (System/currentTimeMillis) start-time)))))

(defn query
  "Perform a database query returning the result set."
  [query-str]
  (println "Executing query" query-str)
  (j/query (config/db) query-str))

(defn get-provider-ids
  "Gets a list of the provider ids in the database. Primarily for enabling migrations of existing
  provider tables."
  []
  (map :provider_id (j/query (config/db) "select provider_id from metadata_db.providers")))

(defn get-regular-providers
  "Gets a list of the regular (not small) providers in the database. Primarily for enabling
  migrations of existing provider tables."
  []
  (remove :small (p/get-providers (config/db))))

(defn get-regular-provider-collection-tablenames
  "Gets a list of all the collection tablenames for regular providers. Primarily for enabling
  migrations of existing regular provider tables."
  []
  (distinct (map #(concept-tables/get-table-name % :collection) (get-regular-providers))))

(defn get-all-concept-tablenames
  "Returns a sequence of table names for the given concept types, or all concept types if none are specified."
  ([]
    (get-all-concept-tablenames :collection :granule :service :tag :access-group))
  ([& concept-types]
   (distinct
     (for [provider (p/get-providers (config/db))
           concept-type concept-types]
       (concept-tables/get-table-name provider concept-type)))))

(defn get-collection-tablenames
  "Gets a list of all the collection tablenames. Primarily for enabling migrations of existing
  provider tables."
  []
  (distinct (map #(concept-tables/get-table-name % :collection) (p/get-providers (config/db)))))

(defn get-granule-tablenames
  "Gets a list of all the granule tablenames. Primarily for enabling migrations of existing
  provider tables."
  []
  (distinct (map #(concept-tables/get-table-name % :granule) (p/get-providers (config/db)))))

(defn sequence-exists?
  "Returns true if the named sequence exists."
  [sequence-name]
  (let [result (j/query
                 (config/db)
                 ["select count(*) as c from all_sequences where sequence_owner='METADATA_DB' and sequence_name=upper(?)" sequence-name])
        seq-count (:c (first result))]
    (not= 0 (int seq-count))))

(defn concept-id-seq-missing?
  "Returns true if concept_id_seq does not exist"
  []
  (let [result (j/query
                 (config/db)
                 "select count(*) as c from all_sequences where sequence_name='CONCEPT_ID_SEQ'")
        seq-count (:c (first result))]
    (= 0 (int seq-count))))

