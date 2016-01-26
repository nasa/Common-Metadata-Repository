(ns migrations.030-assign-transaction-ids-to-collections
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))
            
(defn up
  "Migrates the database up to version 30."
  []
  (println "migrations.030-assign-transaction-ids-to-collections up...")
  (doseq [table (h/get-all-concept-tablenames :collection)]
    (println (str "Updating transaction_id in table " table))
    (j/db-do-commands (config/db) false "SET TRANSACTION READ WRITE")
    (j/db-do-commands (config/db) false (format "LOCK TABLE %s IN EXCLUSIVE MODE" table))
    (doseq [row (h/query (format "SELECT id FROM %s ORDER BY concept_id ASC, revision_id ASC FOR UPDATE" table))]
      (j/db-do-commands (config/db) false (format "UPDATE %s SET transaction_id=GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL WHERE id=%d" table (long (:id row)))))  
   (j/db-do-commands (config/db) false (format "ALTER TABLE %s MODIFY (transaction_id NOT NULL)" table))
   (j/db-do-commands (config/db) false "COMMIT")))
         
(defn down
  "Migrates the database down from version 30."
  []
  (println "migrations.030-assign-transaction-ids-to-collections down...")
  (doseq [table (h/get-all-concept-tablenames :collection)]
    (h/sql (format "ALTER TABLE %s MODIFY (transaction_id NULL)" table))
    (h/sql (format "UPDATE %s SET transaction_id=NULL" table))))