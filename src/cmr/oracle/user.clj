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

(def select-users-tables-template
  "select table_name from all_tables where owner = '%%USERNAME%%'")

(def grant-select-template
  "grant select on %%TABLESPACE%%.%%TABLE%% to %%CMR_USER%%")

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
   (conn/db-spec "sys as sysdba" password)))

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

(defn grant-select-priviliges
  "Grant select priviliges from one user's tables to another user."
  [db from-tablespace from-user to-user]
  (let [select-tables-sql (replace-values {"USERNAME" from-user} select-users-tables-template)
        tables (j/query db select-tables-sql)]
    (doseq [{:keys [table_name]} tables]
      (let [grant-sql (replace-values {"TABLESPACE" from-tablespace
                                        "TABLE" table_name
                                        "CMR_USER" to-user}
                                       grant-select-template)]
      (j/db-do-commands db grant-sql)))))

