(ns cmr.bootstrap.test.mock-catalog-rest
  "Contains functions for setting up and destroying mock catalog rest tables"
  (:require [cmr.bootstrap.test.catalog-rest-ddl-sql :as ddl]
            [cmr.oracle.connection :as oracle]
            [clojure.java.jdbc :as j]
            ))


(defn db-user
  []
  oracle/db-username)

(defn create-providers-table
  [conn]
  (j/db-do-commands conn (ddl/create-providers-table-sql (db-user))))


(comment

  (create-providers-table (:db user/system))

)