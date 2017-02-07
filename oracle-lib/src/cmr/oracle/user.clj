(ns cmr.oracle.user
  "Contains functions for creating and dropping users."
  (:require [clojure.java.jdbc :as j]
            [cmr.oracle.connection :as conn]))

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
  "grant select on %%FROM_USER%%.%%TABLE%% to %%CMR_USER%%")

(def grant-any-table-template
  "Grants ability to create and drop tables at will to the user. It also adds create any index which
  is needed if creating a table with an index."
  (str "grant create any table, drop any table, create any index, insert any table, "
       "update any table, delete any table, select any table to %%CMR_USER%%"))

(defn replace-values
  "Replaces values in a sql template using the key values given"
  [key-values template]
  (reduce (fn [temp [key,val]]
            (clojure.string/replace temp (str "%%" key "%%") val))
          template
          key-values))

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

(defn grant-select-privileges
  "Grant select privileges from one user's tables to another user."
  [db from-user to-user]
  (println "Granting select privileges to" to-user "on tables in" from-user)
  (let [select-tables-sql (replace-values {"USERNAME" from-user} select-users-tables-template)
        tables (j/query db select-tables-sql)]
    (when (empty? tables)
      (println "WARNING: Found 0 tables owned by" from-user))
    (doseq [{:keys [table_name]} tables]
      (let [grant-sql (replace-values {"FROM_USER" from-user
                                        "TABLE" table_name
                                        "CMR_USER" to-user}
                                      grant-select-template)]
       (j/db-do-commands db grant-sql)))))

(defn grant-create-drop-any-table-privileges
  "Grant privileges to create and drop any table or modify any table. This is useful in testing
  situations where we want to create test tables and data in another schema."
  [db to-user]
  (println "Granting create and drop any table privileges to" to-user)
  (j/db-do-commands db (replace-values {"CMR_USER" to-user} grant-any-table-template)))
