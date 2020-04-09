(ns cmr.metadata-db.migrations.052-add-coll-ids-catalog-acls
  (:require [clojure.java.jdbc :as j]
            [clojure.edn :as edn]
            [cmr.common.util :as util]
            [config.mdb-migrate-config :as config]
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

(defn up
  "Migrates the database up to version 52."
  []
  (println "cmr.metadata-db.migrations.052-add-coll-ids-catalog-acls up...")
  (doseq [result (h/query "select * from cmr_acls where acl_identity like 'catalog-item%'")]
    (println result)
    (let [{:keys [id metadata deleted]} result
          deleted (= 1 (long deleted))
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          {{:keys [collection-identifier collection-applicable provider-id]} :catalog-item-identity} metadata
          concept-ids (if collection-applicable
                        (get-concept-ids provider-id collection-identifier)
                        nil)
          metadata (if deleted
                     metadata
                     (if (seq concept-ids)
                       (assoc-in metadata [:catalog-item-identity :collection-identifier :concept-ids] concept-ids)
                       metadata))
          metadata (-> metadata
                       pr-str
                       util/string->gzip-bytes)
          result (assoc result :metadata metadata)]
      (j/update! (config/db) "cmr_acls" result ["id = ?" id]))))

(defn down
  "Migrates the database down from version 52."
  []
  (println "cmr.metadata-db.migrations.052-add-coll-ids-catalog-acls down..."))
