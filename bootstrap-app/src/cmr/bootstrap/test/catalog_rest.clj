(ns cmr.bootstrap.test.catalog-rest
  "Contains code to help test bulk database migration. It can insert and delete data from the Catalog
  REST schema"
  (:require [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :as sql :refer [sql select insert from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [cmr.bootstrap.data.bulk-migration :as bm]
            [cmr.common.concepts :as concepts]
            [cmr.common.date-time-parser :as p]
            [cmr.metadata-db.data.oracle.concepts :as mdb-concepts]
            [clj-time.core :as t]
            [clj-time.coerce :as cr]))

(defn- execute-sql
  "Executes the sql which presumably has side effects. Returns nil."
  [system sql]
  (info "Executing sql:" sql)
  (j/execute! (:db system) [sql]))

(defn- create-datasets-table-sql
  "Creates the SQL to create the datasets table. Only adds columns which are used during bulk
  migration."
  [system provider-id]
  (apply
    format
    "CREATE TABLE %s (
    id NUMBER(38,0) NOT NULL,
    echo_collection_id VARCHAR2(255 CHAR) NOT NULL,
    dataset_id VARCHAR2(1030 CHAR) NOT NULL,
    compressed_xml BLOB NOT NULL,
    ingest_updated_at TIMESTAMP(6) NOT NULL,
    short_name VARCHAR2(85 CHAR) NOT NULL,
    version_id VARCHAR2(80 CHAR) NOT NULL,
    xml_mime_type VARCHAR2(255 CHAR) NOT NULL,
    delete_time TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT %s_eci UNIQUE (echo_collection_id),
    CONSTRAINT %s_sv UNIQUE (short_name, version_id),
    CONSTRAINT %s_did UNIQUE (dataset_id))"
    (bm/catalog-rest-dataset-table system provider-id)
    (repeat 3 provider-id)))

(defn- create-granules-table-sql
  "Creates the SQL to create the granules table. Only adds columns which are used during bulk
  migration."
  [system provider-id]
  (format
    "CREATE TABLE %s (
    id NUMBER(38,0) NOT NULL,
    echo_granule_id VARCHAR2(255 CHAR) NOT NULL,
    granule_ur VARCHAR2(250 CHAR) NOT NULL,
    compressed_xml BLOB NOT NULL,
    dataset_record_id NUMBER(38,0) NOT NULL,
    xml_mime_type VARCHAR2(255 CHAR) NOT NULL,
    ingest_updated_at TIMESTAMP (6) NOT NULL,
    delete_time TIMESTAMP (6),
    PRIMARY KEY (ID),
    CONSTRAINT %s_gur UNIQUE (granule_ur),
    CONSTRAINT %s_egi UNIQUE (echo_granule_id),
    CONSTRAINT fk_%s_granule_dataset FOREIGN KEY (DATASET_RECORD_ID)
    REFERENCES %s (ID) ON DELETE CASCADE)"
    (bm/catalog-rest-granule-table system provider-id)
    provider-id provider-id provider-id
    (bm/catalog-rest-dataset-table system provider-id)))

(defn- drop-datasets-table-sql
  "Creates the SQL to drop the dataset table."
  [system provider-id]
  (str "drop table " (bm/catalog-rest-dataset-table system provider-id)))

(defn- drop-granules-table-sql
  "Creates the SQL to drop the granule table."
  [system provider-id]
  (str "drop table " (bm/catalog-rest-granule-table system provider-id)))

(defn create-provider
  "Creates the provider related tables in the Catalog REST schema."
  [system provider-id]
  (execute-sql system (create-datasets-table-sql system provider-id))
  (execute-sql system (create-granules-table-sql system provider-id)))

(defn drop-provider
  "Removes the provider related tables in the Catalog REST schema."
  [system provider-id]
  (execute-sql system (drop-granules-table-sql system provider-id))
  (execute-sql system (drop-datasets-table-sql system provider-id)))

(defmulti save-concept
  "Saves the given concept to the correct database"
  (fn [system concept]
    (:concept-type concept)))

(defn- concept-id->numeric-id
  [concept-id]
  (-> concept-id concepts/parse-concept-id :sequence-number))

(defmethod save-concept :collection
  [system concept]
  (let [{:keys [provider-id concept-id metadata]} concept
        {:keys [short-name version-id entry-title delete-time]} (:extra-fields concept)
        table (bm/catalog-rest-dataset-table system provider-id)
        numeric-id (concept-id->numeric-id concept-id)
        stmt (format "insert into %s (id, echo_collection_id, dataset_id, compressed_xml, ingest_updated_at,
                     short_name, version_id, xml_mime_type, delete_time) values (?,?,?,?,?,?,?,?,?)"
                     table)
        insert-args [numeric-id concept-id entry-title (mdb-concepts/string->gzip-bytes metadata)
                     (cr/to-sql-time (t/now)) short-name version-id
                     (mdb-concepts/mime-type->db-format (:format concept))
                     (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))]]
    (j/db-do-prepared (:db system) stmt insert-args)))

;; Note that this assumes the native id of the granule is the granule ur.
(defmethod save-concept :granule
  [system concept]
  (let [{:keys [provider-id concept-id native-id metadata]} concept
        {:keys [delete-time parent-collection-id]} (:extra-fields concept)
        table (bm/catalog-rest-granule-table system provider-id)
        numeric-id (concept-id->numeric-id concept-id)
        numeric-collection-id (concept-id->numeric-id parent-collection-id)
        stmt (format "insert into %s (id, echo_granule_id, granule_ur, compressed_xml,
                     dataset_record_id, xml_mime_type, ingest_updated_at, delete_time)
                     values (?,?,?,?,?,?,?,?)"
                     table)
        insert-args [numeric-id concept-id native-id (mdb-concepts/string->gzip-bytes metadata)
                     numeric-collection-id (mdb-concepts/mime-type->db-format (:format concept))
                     (cr/to-sql-time (t/now))
                     (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))]]
    (j/db-do-prepared (:db system) stmt insert-args)))



(comment

  (def system (get-in user/system [:apps :bootstrap]))

  (create-provider system "JPROV")
  (drop-provider system "JPROV")

  (def example-collection
    {:provider-id "JPROV"
     :concept-type :collection
     :concept-id "C1-JPROV"
     :metadata "the metadata"
     :format "application/echo10+xml"
     :extra-fields {:short-name "short"
                    :version-id "V1"
                    :entry-title "Entry 1"
                    :delete-time "2014-05-05T00:00:00Z"}})

  (def example-granule
    {:provider-id "JPROV"
     :concept-type :granule
     :concept-id "G1-JPROV"
     :metadata "the metadata"
     :format "application/echo10+xml"
     :native-id "granule ur"
     :extra-fields {:parent-collection-id "C1-JPROV"
                    :delete-time "2014-05-05T00:00:00Z"}})


  (save-concept system example-collection)

  (save-concept system example-granule)




)

