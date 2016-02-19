(ns migrations.034-generate-tag-associations-from-tags
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.services.concept-service :as cs]))

; (defn up
;   "Migrates the database up to version 34."
;   []
;   (println "migrations.034-generate-tag-associations-from-tags up...")
;   (let [context {:system {:db (config/db)}}]
;     (doseq [{:keys [concept_id revision_id]} (h/query "select concept_id, max(revision_id) as revision_id from cmr_tags group by concept_id")]
;       (println (format "Processing tag [%s]" concept_id))
;       (let [tag (cs/get-concept context concept_id revision_id)]
;
;         ()))))

(defn down
  "Migrates the database down from version 34."
  []
  (println "migrations.034-generate-tag-associations-from-tags down..."))
