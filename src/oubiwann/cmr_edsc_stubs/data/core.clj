(ns oubiwann.cmr-edsc-stubs.data.core
  (:require [clojure.java.jdbc :as jdbc])
  (:import (clojure.lang Keyword)))

(defn get-db
  [system]
  (get-in system [:apps :metadata-db :db :spec]))

(defn query
  [system sql]
  (jdbc/with-db-connection [db-conn (get-db system)]
    (pprint (jdbc/query db-conn [sql]))))

(defn insert
  [system ^Keyword table data]
  (jdbc/with-db-connection [db-conn (get-db system)]
    (jdbc/insert! db-conn table data)))
