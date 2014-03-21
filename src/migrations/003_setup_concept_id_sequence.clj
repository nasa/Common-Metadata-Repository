(ns migrations.003-setup-concept-id-sequence
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 3."
  []
  (println "migrations.003-setup-concept-id-sequence up...")
  (j/db-do-commands config/db "CREATE SEQUENCE concept_id_seq
                              MINVALUE 1
                              START WITH 1
                              INCREMENT BY 1
                              CACHE 20"))

(defn down
  "Migrates the database down from version 3."
  []
  (println "migrations.003-setup-concept-id-sequence down...")
  (j/db-do-commands config/db "DROP SEQUENCE concept_id_seq"))