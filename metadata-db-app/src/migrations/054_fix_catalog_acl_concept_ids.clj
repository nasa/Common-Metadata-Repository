(ns migrations.054-fix-catalog-acl-concept-ids
  (:require [clojure.java.jdbc :as j]
            [clojure.edn :as edn]
            [cmr.common.util :as util]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.data.oracle.providers :as providers]
            [clojure.string :as string]))

(defn up
  "Migrates the database up to version 54."
  []
  (println "migrations.054-fix-catalog-acl-concept-ids up...")
  (doseq [result (h/query "select * from cmr_acls where acl_identity like 'catalog-item%'")]
    (println result)
    (let [{:keys [id metadata deleted]} result
          deleted (= 1 (long deleted))
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (string/replace #"Executing.*\n" "")
                       (string/replace #"entry_title.*\n" "")
                       (edn/read-string))
          metadata (if deleted
                     metadata
                     (update-in metadata [:catalog-item-identity :collection-identifier] dissoc :concept-ids))
          metadata (-> metadata
                       util/remove-nil-keys
                       pr-str
                       util/string->gzip-bytes)
          result (assoc result :metadata metadata)]
      (j/update! (config/db) "cmr_acls" result ["id = ?" id]))))


(defn down
  "Migrates the database down from version 54."
  []
  (println "migrations.054-fix-catalog-acl-concept-ids down...")
  (throw (Exception. "This migration does not support 'down'")))
