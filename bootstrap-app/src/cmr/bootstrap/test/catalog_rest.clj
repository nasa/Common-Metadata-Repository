(ns cmr.bootstrap.test.catalog-rest
  "Contains code to help test bulk database migration. It can insert and delete data from the Catalog
  REST schema"
  (:require
   [clj-time.coerce :as cr]
   [clojure.java.jdbc :as j]
   [cmr.bootstrap.data.migration-utils :as mu]
   [cmr.common.concepts :as concepts]
   [cmr.common.date-time-parser :as p]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.util :as mdb-util]
   [cmr.oracle.connection :as oracle]))

(defn- execute-sql
  "Executes the sql which presumably has side effects. Returns nil."
  [system sql]
  ;; Uncomment to print out the sql
  ; (info "Executing sql:" sql)
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
    (mu/catalog-rest-table system provider-id :collection)
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
    (mu/catalog-rest-table system provider-id :granule)
    provider-id provider-id provider-id
    (mu/catalog-rest-table system provider-id :collection)))

(defn- drop-datasets-table-sql
  "Creates the SQL to drop the dataset table."
  [system provider-id]
  (str "drop table " (mu/catalog-rest-table system provider-id :collection)))

(defn- drop-granules-table-sql
  "Creates the SQL to drop the granule table."
  [system provider-id]
  (str "drop table " (mu/catalog-rest-table system provider-id :granule)))

(defn create-provider
  "Creates the provider related tables in the Catalog REST schema."
  [system provider-id]
  (execute-sql system (create-datasets-table-sql system provider-id))
  (execute-sql system (create-granules-table-sql system provider-id)))

(defn delete-provider
  "Removes the provider related tables in the Catalog REST schema."
  [system provider-id]
  (execute-sql system (drop-granules-table-sql system provider-id))
  (execute-sql system (drop-datasets-table-sql system provider-id)))

(defn- concept-id->numeric-id
  [concept-id]
  (-> concept-id concepts/parse-concept-id :sequence-number))

(defmulti insert-concept
  "Inserts the given concept"
  (fn [system concept]
    (:concept-type concept)))

(defn insert-concepts
  "Inserts all the concepts"
  [system concepts]
  (doseq [concept concepts]
    (insert-concept system concept)))

(def mime-type->db-format
  "Map of mime types to the format to store in the database. Modified to store what Catalog REST uses"
  (assoc mdb-util/mime-type->db-format-map
         mt/iso-smap "ISO-SMAP"))

(defn update-concept
  "Updates the concept in the Catalog REST database"
  [system concept]
  (let [{:keys [provider-id concept-id metadata concept-type]} concept
        {:keys [delete-time]} (:extra-fields concept)
        table (mu/catalog-rest-table system provider-id concept-type)
        numeric-id (concept-id->numeric-id concept-id)
        stmt (format "update %s
                     set compressed_xml = ?, ingest_updated_at = ?, xml_mime_type = ?, delete_time = ?
                     where id = ?"
                     table)
        sql-args [(util/string->gzip-bytes metadata)
                  (cr/to-sql-time (oracle/current-db-time (:db system)))
                  (mime-type->db-format (:format concept))
                  (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))
                  numeric-id]]
    (j/db-do-prepared (:db system) stmt sql-args)))

(defn update-concepts
  "Updates all the concepts"
  [system concepts]
  (doseq [concept concepts]
    (update-concept system concept)))

(defn delete-concept
  "Deletes the concept in the Catalog REST database"
  [system concept]
  (let [{:keys [provider-id concept-id concept-type]} concept
        table (mu/catalog-rest-table system provider-id concept-type)
        numeric-id (concept-id->numeric-id concept-id)
        stmt (format "delete from %s where id = ?" table)]
    (j/db-do-prepared (:db system) stmt [numeric-id])))

(defn delete-concepts
  "Deletes all the concepts"
  [system concepts]
  (doseq [concept concepts]
    (delete-concept system concept)))

(defmethod insert-concept :collection
  [system concept]
  (let [{:keys [provider-id concept-id metadata]} concept
        {:keys [short-name version-id entry-title delete-time]} (:extra-fields concept)
        table (mu/catalog-rest-table system provider-id :collection)
        numeric-id (concept-id->numeric-id concept-id)
        stmt (format "insert into %s (id, echo_collection_id, dataset_id, compressed_xml, ingest_updated_at,
                     short_name, version_id, xml_mime_type, delete_time) values (?,?,?,?,?,?,?,?,?)"
                     table)
        sql-args [numeric-id concept-id entry-title (util/string->gzip-bytes metadata)
                  (cr/to-sql-time (oracle/current-db-time (:db system))) short-name version-id
                  (mime-type->db-format (:format concept))
                  (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))]]
    (j/db-do-prepared (:db system) stmt sql-args)))

;; Note that this assumes the native id of the granule is the granule ur.
(defmethod insert-concept :granule
  [system concept]
  (let [{:keys [provider-id concept-id native-id metadata]} concept
        {:keys [delete-time parent-collection-id]} (:extra-fields concept)
        table (mu/catalog-rest-table system provider-id :granule)
        numeric-id (concept-id->numeric-id concept-id)
        numeric-collection-id (concept-id->numeric-id parent-collection-id)
        stmt (format "insert into %s (id, echo_granule_id, granule_ur, compressed_xml,
                     dataset_record_id, xml_mime_type, ingest_updated_at, delete_time)
                     values (?,?,?,?,?,?,?,?)"
                     table)
        sql-args [numeric-id concept-id native-id (util/string->gzip-bytes metadata)
                  numeric-collection-id (mime-type->db-format (:format concept))
                  (cr/to-sql-time (oracle/current-db-time (:db system)))
                  (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))]]
    (j/db-do-prepared (:db system) stmt sql-args)))

(comment

  (def system (get-in user/system [:apps :bootstrap]))

  (create-provider system "JPROV")
  (drop-provider system "JPROV")

  (def example-collection
    {:provider-id "JPROV"
     :concept-type :collection
     :concept-id "C1-JPROV"
     :metadata "the metadata"
     :format mt/echo10
     :extra-fields {:short-name "short"
                    :version-id "V1"
                    :entry-id "short_V1"
                    :entry-title "Entry 1"
                    :delete-time "2014-05-05T00:00:00Z"}})

  (def example-granule
    {:provider-id "JPROV"
     :concept-type :granule
     :concept-id "G1-JPROV"
     :metadata "the metadata"
     :format mt/echo10
     :native-id "granule ur"
     :extra-fields {:parent-collection-id "C1-JPROV"
                    :delete-time "2014-05-05T00:00:00Z"
                    :granule-ur "granule ur"}})


  (insert-concept system example-collection)

  (insert-concept system example-granule))
