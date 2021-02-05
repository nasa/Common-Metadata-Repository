(ns config.ingest-migrate-helper
  "Contains helper functions for performing database migrations"
  (:require
   [clojure.java.jdbc :as j]
   [config.ingest-migrate-config :as config]))

(defn sql
  "Applies the sql update"
  [& stmt-strs]
  (println "Applying" stmt-strs)
  (let [start-time (System/currentTimeMillis)]
    (apply j/db-do-commands (config/db) stmt-strs)
    (println (format "Finished %s in [%s] ms."
                     stmt-strs
                     (- (System/currentTimeMillis) start-time)))))
