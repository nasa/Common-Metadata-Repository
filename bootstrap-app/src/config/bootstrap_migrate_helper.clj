(ns config.bootstrap-migrate-helper
  "Contains helper functions for performing database migrations"
  (:require [clojure.java.jdbc :as j]
            [config.bootstrap-migrate-config :as config]))

(defn sql
  "Applies the sql update"
  [stmt-str]
  (println "Applying update" stmt-str)
  (j/db-do-commands (config/db) stmt-str))
