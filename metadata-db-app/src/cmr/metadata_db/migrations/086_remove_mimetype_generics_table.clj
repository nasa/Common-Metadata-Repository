(ns cmr.metadata-db.migrations.086-remove-mimetype-generics-table
   (:require
   [cheshire.core :as json]
   [cmr.common.util :as util]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 86."
  []
  (println "cmr.metadata-db.migrations.086-remove-mimetype-generics-table up...")
  (h/sql "ALTER TABLE CMR_GENERIC_DOCUMENTS ADD MIME_TYPE VARCHAR(255)")
  ;;Retrieve the version # from the format column and prepdend that to the restored MIME_TYPE column value for each row
  (h/sql "UPDATE CMR_GENERIC_DOCUMENTS SET MIME_TYPE = CONCAT('application/vnd.nasa.cmr.umm+json;', SUBSTR(FORMAT, -5))"))

(defn down
  "Migrates the database down from version 86."
  []
  (println "cmr.metadata-db.migrations.086-remove-mimetype-generics-table down...")
  (h/sql "ALTER TABLE CMR_GENERIC_DOCUMENTS DROP COLUMN MIME_TYPE"))
