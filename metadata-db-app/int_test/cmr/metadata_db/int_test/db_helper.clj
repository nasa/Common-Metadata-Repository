(ns cmr.metadata-db.int-test.db-helper
  "Provides implementations needed to directly manipulate database"
  (:require [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [clojure.java.jdbc :as j]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.config :as config]
            [clj-time.coerce :as cr])
  (:import cmr.oracle.connection.OracleStore
           oracle.sql.TIMESTAMPTZ))

(def db-holder
  "holds db connection"
  (atom nil))

(defn get-db
  "Returns the db connection"
  []
  (if @db-holder
    @db-holder
    (reset! db-holder (lifecycle/start (oracle/create-db (config/db-spec "metadata-db-test")) {}))))

(defn update-concept-revision-date
  "Updateds the concept revision-date to the given value."
  [concept revision-date]
  (let [db (get-db)
        {:keys [concept-type provider-id concept-id revision-id]} concept
        table (tables/get-table-name provider-id concept-type)
        stmt (format "UPDATE %s
                     SET revision_date = ?
                     WHERE concept_id = ? and revision_id = ?"
                     table)
        sql-args [(cr/to-sql-time revision-date) concept-id revision-id]]
    (j/db-do-prepared db stmt sql-args)))


