(ns migrations.057-regenerate-collection-identifiers
  (:require
   [clojure.java.jdbc :as j]
   [clojure.edn :as edn]
   [cmr.common.util :as util]
   [config.migrate-config :as config]
   [config.mdb-migrate-helper :as h]
   [cmr.metadata-db.data.oracle.providers :as providers]
   [clojure.string :as string]))

(defn- get-concept-ids
  "For a given provider and entry-titles in collection-identifier, returns list of collection concept ids"
  [provider-id collection-identifier]
  (when-let [entry-titles (get collection-identifier :entry-titles)]
    (let [provider (-> (h/get-provider provider-id)
                       providers/dbresult->provider)
          t (h/get-provider-collection-tablename provider)]
      (for [entry-title entry-titles
            :let [result (j/query (config/db) [(format "select distinct concept_id from metadata_db.%s where entry_title = ?" t)
                                               entry-title])]]
        (:concept_id (first result))))))

(defn- get-entry-titles
  "For a given provider and concept-ids in collection-identifier, returns list of collection entry-titles"
  [provider-id collection-identifier]
  (when-let [concept-ids (get collection-identifier :concept-ids)]
    (let [provider (-> (h/get-provider provider-id)
                       providers/dbresult->provider)
          t (h/get-provider-collection-tablename provider)]
      (for [concept-id concept-ids
            :let [result (j/query (config/db) [(format "select distinct entry_title from metadata_db.%s where concept_id = ?" t)
                                               concept-id])]]
        (:entry_title (first result))))))

(defn up
  "Migrates the database up to version 57."
  []
  (println "migrations.057-regenerate-collection-identifiers up...")
  (doseq [result (h/query "select * from cmr_acls where acl_identity like 'catalog-item%'")]
    (println result)
    (let [{:keys [id metadata deleted]} result
          deleted (= 1 (long deleted))
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          {{:keys [collection-identifier collection-applicable granule-applicable provider-id]} :catalog-item-identity} metadata
          concept-ids (if (or collection-applicable granule-applicable)
                        (get-concept-ids provider-id collection-identifier)
                        nil)
          entry-titles (if (or collection-applicable granule-applicable)
                        (get-entry-titles provider-id collection-identifier)
                        nil)
          metadata (if deleted
                     metadata
                     (if (seq concept-ids)
                       (assoc-in metadata [:catalog-item-identity :collection-identifier :concept-ids] concept-ids)
                       metadata))
          metadata (if deleted
                     metadata
                     (if (seq entry-titles)
                       (assoc-in metadata [:catalog-item-identity :collection-identifier :entry-titles] entry-titles)
                       metadata))
          metadata (-> metadata
                       pr-str
                       util/string->gzip-bytes)
          result (assoc result :metadata metadata)]
      (j/update! (config/db) "cmr_acls" result ["id = ?" id]))))

(defn down
  "Migrates the database down from version 57."
  []
  (println "migrations.057-regenerate-collection-identifiers down..."))
