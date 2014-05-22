(ns cmr.bootstrap.test.catalog-rest-ddl-sql
  "Contains functions to create sql statements to create the mock catalog rest tables.")

(defn create-providers-table-sql
  [user]
  (format "CREATE TABLE \"%s\".\"PROVIDERS\"
          ( \"ID\" NUMBER(38,0) NOT NULL ENABLE,
          \"CREATED_AT\" TIMESTAMP (6) NOT NULL ENABLE,
          \"UPDATED_AT\" TIMESTAMP (6) NOT NULL ENABLE,
          \"PROVIDER_ID\" VARCHAR2(255 CHAR),
          \"DATASET_ACL_HASH\" VARCHAR2(255 CHAR) DEFAULT '',
          \"REST_ONLY\" NUMBER(1,0) DEFAULT 0,
          PRIMARY KEY (\"ID\")
          )"
          user ))