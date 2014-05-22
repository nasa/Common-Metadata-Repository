(ns cmr.oracle.user
  "Contains functions for creating and dropping users."
  (:require [clojure.java.jdbc :as j]
            [cmr.oracle.connection :as conn]))

;; TODO - need to decide which tablespaces to use
; "create user %%CMR_USER%%
; profile default
; identified by %%CMR_PASSWORD%%
; default tablespace %%DATA_TABLESPACE%%
; temporary tablespace %%TEMP_TABLESPACE%%
; quota unlimited on %%DATA_TABLESPACE%%
; quota unlimited on %%INDEX_TABLESPACE%%
; account unlock"


(def create-user-sql-template
  "create user %%CMR_USER%%
  profile default
  identified by %%CMR_PASSWORD%%
  account unlock")

(def drop-user-sql-template
  "drop user %%CMR_USER%% cascade")

(def grant-sql-templates
  ["grant create session to %%CMR_USER%%"
   "grant create table to %%CMR_USER%%"
   "grant create sequence to %%CMR_USER%%"
   "grant create view to %%CMR_USER%%"
   "grant create procedure to %%CMR_USER%%"
   "grant unlimited tablespace to %%CMR_USER%%"])

(defn replace-values
  "Replaces values in a sql template using the key values given"
  [key-values template]
  (reduce (fn [temp [key,val]]
            (clojure.string/replace temp (str "%%" key "%%") val))
          template
          key-values))

(defn sys-dba-conn
  "Returns a database connection to the database as the sysdba user."
  ([]
   (sys-dba-conn "oracle"))
  ([password]
   (conn/db-spec "sys as sydba" password)))

(defn create-user
  "Creates the given user in the database."
  [db user password]
  (let [replacer (partial replace-values {"CMR_USER" user
                                          "CMR_PASSWORD" password})
        create-user-sql (replacer create-user-sql-template)
        grant-sqls (map replacer grant-sql-templates)]
    (j/db-do-commands db create-user-sql)

    (doseq [sql grant-sqls]
      (j/db-do-commands db sql))))

(defn drop-user
  "Deletes the user from the database"
  [db user]
  (j/db-do-commands db (replace-values {"CMR_USER" user}
                                       drop-user-sql-template)))

