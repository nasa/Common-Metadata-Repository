(ns cmr.metadata-db.migrations.038-add-indices-to-tag-associations-table
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  []
  (println "cmr.metadata-db.migrations.038-add-indices-to-tag-associations-table up...")
  ;; We need the following indices for searching tag associations during tag association validation
  ;; and dissociate tags.
  (h/sql "CREATE INDEX tag_assoc_tkcid ON cmr_tag_associations (tag_key, associated_concept_id)")
  (h/sql "CREATE INDEX tag_assoc_tkcrid ON cmr_tag_associations (tag_key, associated_concept_id, associated_revision_id)"))

(defn down
  []
  (println "cmr.metadata-db.migrations.038-add-indices-to-tag-associations-table down...")
  (h/sql "DROP INDEX tag_assoc_tkcid")
  (h/sql "DROP INDEX tag_assoc_tkcrid"))
