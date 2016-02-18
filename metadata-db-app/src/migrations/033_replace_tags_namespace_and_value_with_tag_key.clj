(ns migrations.033-replace-tags-namespace-and-value-with-tag-key
  (:require [clojure.java.jdbc :as j]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [config.migrate-config :as config]
            [cmr.common.util :as util]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 33."
  []
  (println "migrations.033-replace-tags-namespace-and-value-with-tag-key up...")
  (doseq [result (h/query "SELECT * from cmr_tags")]
    (println (format "Processing concept-id [%s] revision [%d]"
                     (:concept_id result)
                     (long (:revision_id result))))
    (let [{:keys [id metadata]} result
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          ;; Some rows may already have a tag-key instead of namespace and value.
          {:keys [namespace value tag-key]} metadata
          tag-key (or tag-key
                      (str/lower-case (str namespace "." value)))
          metadata (-> metadata
                       (assoc :tag-key tag-key)
                       (dissoc :namespace :value :category)
                       (prn-str)
                       (util/string->gzip-bytes))
          result (assoc result :metadata metadata :native_id tag-key)]
      (j/update! (config/db) "cmr_tags" result ["id = ?" id]))))

(defn down
  "Migrates the database down from version 33."
  []
  (println "migrations.033-replace-tags-namespace-and-value-with-tag-key down...")
  (println "NOTE: category field and original native-id cannot be recovered")
  (doseq [result (h/query "SELECT * from cmr_tags")]
    (let [{:keys [id metadata]} result
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          tag-key (:tag-key metadata)
          ;; There is no way to properly recover a namespace and value that have a 
          ;; "." in the value.
          [_ namespace value] (re-matches #"(.*)\.(.*$)" tag-key)
          metadata (-> metadata
                       (assoc :namespace namespace
                              :value value)
                       (dissoc :tag-key)
                       (prn-str)
                       (util/string->gzip-bytes))
          result (assoc result :metadata metadata)]
      (j/update! (config/db) "cmr_tags" result ["id = ?" id]))))
