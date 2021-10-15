(ns cmr.metadata-db.migrations.075-add-provider-consortiums
  (:require
   [cheshire.core :as json]
   [cmr.common.util :as util]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [config.mdb-migrate-helper :as helper]))

(defn up
  "Migrates the database up to version 75.
   The consortium table will hold a comma deliminated list of organization and
   project names which will be passed to the indexer allowing it to create
   indexes, tags, or other such objects at ingest time. An example value is
   'CWIC, FEDEO, CEOS, EOSDIS, GEOSS' which is 32 letters long, so 64 should be
   fine for todays requirments and future additions."
  []
  (println "cmr.metadata-db.migrations.075-add-provider-consortiums up...")
  (helper/sql "alter table METADATA_DB.providers add consortiums VARCHAR2(64)"))

(defn down
  "Migrates the database down from version 75. Only need to drop the new field."
  []
  (println "cmr.metadata-db.migrations.074-update-subscription-table down.")
  (helper/sql "alter table METADATA_DB.providers drop column consortiums"))
