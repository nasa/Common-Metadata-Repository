(ns migrations.003-setup-get-concept-temporary-table
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 3."
  []
  (println "migrations.003-setup-get-concept-temporary-table up...")
  #_(j/db-do-commands config/db "CREATE GLOBAL TEMPORARY TABLE METADATA_DB.get_concepts_work_area
                         (concept_id VARCHAR(255),
                         revision_id INTEGER)
                         ON COMMIT DELETE ROWS")
  
  (j/db-do-commands config/db "CREATE TABLE METADATA_DB.get_concepts_work_area
                         (concept_id VARCHAR(255),
                         revision_id INTEGER)"))



(defn down
  "Migrates the database down from version 3."
  []
  (println "migrations.003-setup-get-concept-temporary-table down...")
  (j/db-do-commands config/db "DROP TABLE METADATA_DB.get_concepts_work_area"))