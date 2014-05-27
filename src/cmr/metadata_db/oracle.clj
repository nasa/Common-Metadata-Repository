(ns cmr.metadata-db.oracle
  (:require [cmr.oracle.connection :as oracle]))

(def db-atom (atom nil))

(defn get-db
  "Returns the db-atom that is populated with db instance"
  []
  (if @db-atom
    @db-atom
    (reset! db-atom (oracle/create-db (oracle/db-spec)))))