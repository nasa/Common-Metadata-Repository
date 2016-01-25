(ns migrations.030-assign-transaction-ids-to-collections
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))
            
(defn up
  "Migrates the database up to version 30."
  []
  (println "migrations.030-assign-transaction-ids-to-collections up...")
  (j/db-do-commands (config/db) false "SET TRANSACTION READ WRITE")
  ;; Must lock table to avoid new data being written which might get lower transaction-ids
  (j/db-do-commands (config/db) false "LOCK TABLE small_prov_collections IN EXCLUSIVE MODE")
  (h/query "SELECT id FROM small_prov_collections ORDER BY concept_id ASC, revision_id ASC FOR UPDATE")
  (j/db-do-commands (config/db) false "UPDATE small_prov_collections SET transaction_id=GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL")
  (j/db-do-commands (config/db) false "COMMIT")
  (doseq [table (h/get-all-concept-tablenames :collection)]
    (println (str "Updating transaction_id in table " table))
    (j/db-do-commands (config/db) false "SET TRANSACTION READ WRITE")
    (j/db-do-commands (config/db) false "LOCK TABLE small_prov_collections IN EXCLUSIVE MODE")
    (doseq [row (h/query (format "SELECT id FROM %s WHERE 1=1 ORDER BY concept_id ASC, revision_id ASC FOR UPDATE" table))]
      (j/db-do-commands (config/db) false (format "UPDATE %s SET transaction_id=GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL WHERE id=%d" table (long (:id row)))))  
   (j/db-do-commands (config/db) false "COMMIT")))
         

(defn down
  "Migrates the database down from version 30."
  []
  (println "migrations.030-assign-transaction-ids-to-collections down...")
  (doseq [table (h/get-all-concept-tablenames :collection)]
    (h/sql (format "UPDATE %s SET transaction_id=0" table)))
  (h/sql "UPDATE small_prov_collections SET transaction_id=0"))