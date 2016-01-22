(ns migrations.029-add-transaction-id-into-collections
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 29."
  []
  (println "migrations.029-add-transaction-id-into-collections up..."))

(defn down
  "Migrates the database down from version 29."
  []
  (println "migrations.029-add-transaction-id-into-collections down..."))