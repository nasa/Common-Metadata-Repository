(ns cmr.metadata-db.migrations.067-add-indices-to-variable-service-association-tables
  (:require
   [config.mdb-migrate-helper :as h]))

(defn- add-variable-associations-indices
  []
  (h/sql "CREATE INDEX v_assoc_vcid ON cmr_variable_associations (variable_concept_id, revision_id)")
  (h/sql "CREATE INDEX v_assoc_vcarid ON cmr_variable_associations (variable_concept_id, associated_concept_id, associated_revision_id)"))

(defn- drop-variable-associations-indices
  []
  (h/sql "DROP INDEX v_assoc_vcid")
  (h/sql "DROP INDEX v_assoc_vcarid"))

(defn- add-service-associations-indices
  []
  (h/sql "CREATE INDEX s_assoc_scid ON cmr_service_associations (service_concept_id, revision_id)")
  (h/sql "CREATE INDEX s_assoc_scarid ON cmr_service_associations (service_concept_id, associated_concept_id, associated_revision_id)"))

(defn- drop-service-associations-indices
  []
  (h/sql "DROP INDEX s_assoc_scid")
  (h/sql "DROP INDEX s_assoc_scarid"))

(defn up
  "Migrates the database up to version 67."
  []
  (println "cmr.metadata-db.migrations.067-add-indices-to-variable-service-association-tables up...")
  (add-variable-associations-indices)
  (add-service-associations-indices))

(defn down
  "Migrates the database down from version 67."
  []
  (println "cmr.metadata-db.migrations.067-add-indices-to-variable-service-association-tables down...")
  (drop-variable-associations-indices)
  (drop-service-associations-indices))
