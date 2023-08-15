(ns cmr.aurora.user
  "Contains functions for creating and dropping users."
  (:require [clojure.java.jdbc :as j]
            [cmr.aurora.config :as config]
            [cmr.aurora.connection :as conn]))

(def create-user-sql-template
  "CREATE USER %%CMR_USER%% WITH PASSWORD '%%CMR_PASSWORD%%'")

(def create-schema-make-default-templates
  ["CREATE SCHEMA %%CMR_USER%%"
   "ALTER USER %%CMR_USER%% SET search_path = %%CMR_USER%%"])

(def grant-sql-templates
  ["GRANT CONNECT ON DATABASE %%DATABASE_NAME%% TO %%CMR_USER%%"
   "GRANT CREATE ON SCHEMA %%SCHEMA%% TO %%CMR_USER%%"
   "GRANT USAGE ON SCHEMA %%SCHEMA%% TO %%CMR_USER%%"])

(def select-users-tables-template
  "SELECT table_name
   FROM information_schema.tables
   WHERE table_schema = '%%USERNAME%%'")

(def grant-select-template
  "grant select on %%FROM_USER%%.%%TABLE%% to %%CMR_USER%%")

(def grant-any-table-template
  "Grants ability to create and drop tables at will to the user. It also adds create any index which
  is needed if creating a table with an index.
  Note that in PostgreSQL, users can only be granted privileges on all tables within a specific schema,
  and there is no direct equivalent of ANY privileges as in Oracle."
  "GRANT ALL PRIVILEGES ON SCHEMA %%SCHEMA%% TO %%CMR_USER%%")

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
                                          "CMR_PASSWORD" password
                                          "DATABASE_NAME" (config/aurora-db-name)
                                          "SCHEMA" user})
        create-user-sql (replacer create-user-sql-template)
        default-schema-sqls (map replacer create-schema-make-default-templates)
        grant-sqls (map replacer grant-sql-templates)]
    (j/db-do-commands db create-user-sql)

    (doseq [sql (vec (concat default-schema-sqls grant-sqls))]
      (j/db-do-commands db sql))))

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
  "Grant privileges to create and drop any table or modify any table in a schema. This is useful in testing
  situations where we want to create test tables and data in another schema."
  [db to-user schema]
  (println "Granting create and drop any table privileges to" to-user)
  (j/db-do-commands db (replace-values {"CMR_USER" to-user "SCHEMA" schema} grant-any-table-template)))