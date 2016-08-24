(ns migrations.046-add-primary-key-to-providers
  "Adds primary key to the providers table."
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 46."
  []
  (println "migrations.046-add-primary-key-to-providers up...")
  (h/sql "alter table providers drop constraint unique_provider_id")
  (h/sql "alter table providers add constraint providers_pk primary key (provider_id)"))
  ; (h/sql (str "alter table sync_delete_work add constraint sync_delete_work_pk "
  ;             "primary key (concept_id, revision_id)")))
  ; (h/sql (str "alter table get_concepts_work_area add constraint get_concepts_work_area_pk "
              ; "primary key (concept_id, revision_id)")))

(defn down
  "Migrates the database down from version 46."
  []
  (println "migrations.046-add-primary-key-to-providers down...")
  (h/sql "alter table providers drop constraint providers_pk")
  (h/sql (str "alter table providers add constraint unique_provider_id unique(provider_id) "
              "using index (create unique index provider_id_index on providers(provider_id))")))
  ; (h/sql "alter table sync_delete_work drop constraint sync_delete_work_pk")
  ; (h/sql "alter table get_concepts_work_area drop constraint get_concepts_work_area_pk"))
