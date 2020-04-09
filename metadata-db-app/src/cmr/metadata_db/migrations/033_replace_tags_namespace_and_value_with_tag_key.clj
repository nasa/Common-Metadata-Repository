(ns cmr.metadata-db.migrations.033-replace-tags-namespace-and-value-with-tag-key
  (:require [clojure.java.jdbc :as j]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [config.mdb-migrate-config :as config]
            [cmr.common.util :as util]
            [config.mdb-migrate-helper :as h]))

(defn- print-message
  [result]
  (println (format "Processing concept-id [%s] revision [%d]"
                   (:concept_id result)
                   (long (:revision_id result)))))

(defn up
  "Migrates the database up to version 33."
  []
  (println "cmr.metadata-db.migrations.033-replace-tags-namespace-and-value-with-tag-key up...")
  (doseq [result (h/query "SELECT * from cmr_tags")]
    (print-message result)
    (let [{:keys [id metadata deleted native_id]} result
          deleted (= 1 (long deleted))
          metadata (-> metadata
                       (util/gzip-blob->string)
                       (edn/read-string))
          ;; Some rows may already have a tag-key instead of namespace and value.
          {:keys [namespace value tag-key]} metadata
          tag-key (or tag-key
                      (str/lower-case (str namespace "." value)))
          ;; Tombstones don't have metadata so the best we can do is replace the char 29 with a
          ;; dot.
          native-id (if deleted
                      (str/replace native_id (str (char 29)) ".")
                      tag-key)
          metadata (if deleted
                     metadata
                     (-> metadata
                      (assoc :tag-key tag-key)
                      (dissoc :namespace :value :category)))
          metadata (-> metadata
                       pr-str
                       util/string->gzip-bytes)
          result (assoc result :metadata metadata :native_id native-id)]
      (j/update! (config/db) "cmr_tags" result ["id = ?" id]))))


(defn down
  "Migrates the database down from version 33."
  []
  (println "cmr.metadata-db.migrations.033-replace-tags-namespace-and-value-with-tag-key down...")
  (println "NOTE: category field and original native-id cannot be recovered")
  (doseq [result (h/query "SELECT * from cmr_tags")]
    (print-message result)
    (let [{:keys [id metadata deleted native_id]} result
          result (if deleted
                   ;; Just replace the . at the end of the native id with a group separator.
                  (let [native-id (str/replace native_id #"\.(.*?$)" (str (char 29) "$1"))]
                    (assoc result :native_id native-id))
                  ;; There is no way to properly recover a namespace and value that have a
                  ;; "." in the value.
                  (let [metadata (-> metadata
                                     util/gzip-blob->string
                                     edn/read-string)
                        tag-key (:tag-key metadata)
                        [_ namespace value] (re-matches #"(.*)\.(.*$)" tag-key)
                        native-id (str namespace (char 29) value)
                        metadata (-> metadata
                                     (assoc :namespace namespace :value value)
                                     (dissoc :tag-key)
                                     pr-str
                                     util/string->gzip-bytes)]
                     (assoc result :metadata metadata :native_id native-id)))]
      (j/update! (config/db) "cmr_tags" result ["id = ?" id]))))
