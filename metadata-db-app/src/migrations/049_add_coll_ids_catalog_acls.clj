(ns migrations.049-add-coll-ids-catalog-acls
  (:require [clojure.java.jdbc :as j]
            [clojure.edn :as edn]
            [cmr.common.util :as util]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn- get-concept-ids
  [provider-id collection-identifier]
  (let [entry-titles (get collection-identifier :entry-titles)
        entry-titles (if (seq entry-titles)
                       (interpose "," entry-titles)
                       nil)]
    (when entry-titles
      (flatten
       (for [t (h/get-regular-provider-collection-tablenames)]
         (for [result (h/query (format "SELECT concept-id from % where
																																								provider-id = % and entry-title in (%)" t provider-id entry-titles))]
           (println result)
           (:concept-id result)))))))

(defn up
  "Migrates the database up to version 49."
  []
  (println "migrations.049-add-coll-ids-catalog-acls up...")
  (doseq [result (h/query "SELECT * from cmr_acls where acl_identity like 'catalog-item%'")]
    (println result)
    (let [{:keys [id metadata deleted]} result
          deleted (= 1 (long deleted))
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          {:keys [collection-identifier collection-applicable provider-id]} metadata
          concept-ids (if collection-applicable
                        (get-concept-ids provider-id collection-identifier)
                        nil)
          metadata (if deleted
                     metadata
                     (if (seq concept-ids)
                       (assoc-in metadata :collection-identifier :concept-ids concept-ids)
                       metadata))
          metadata (-> metadata
                       pr-str
                       util/string->gzip-bytes)
          result (assoc result :metadata metadata)]
      (j/update! (config/db) "cmr_acls" result ["id = ?" id]))))

(defn down
  "Migrates the database down from version 49."
  []
  (println "migrations.049-add-coll-ids-catalog-acls down...")
  (doseq [result (h/query "SELECT * from cmr_acls where acl_identity like 'catalog-item%'")]
    (println result)
    (let [{:keys [id metadata deleted]} result
          deleted (= 1 (long deleted))
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          {:keys [collection-identifier collection-applicable]} metadata
          metadata (if deleted
                     metadata
                     (if collection-applicable
                       (update metadata [:collection-identifier] dissoc :concept-ids)))
          metadata (-> metadata
                       pr-str
                       util/string->gzip-bytes)
          result (assoc result :metadata metadata)]
      (j/update! (config/db) "cmr_acls" result ["id = ?" id]))))
