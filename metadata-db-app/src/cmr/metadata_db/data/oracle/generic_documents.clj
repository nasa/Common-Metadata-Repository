(ns cmr.metadata-db.data.oracle.generic-documents
  "Functions for saving, retrieving, deleting generic documents."
  (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as jdbc]
   [clojure.pprint :refer [pprint pp]]
   ;[cmr.common.log :refer [debug info warn error]]
   [cmr.common.time-keeper :as tkeep]
   [cmr.common.util :as cutil]
   ;[cmr.metadata-db.data.oracle.concepts :as concepts]
   ;[cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.metadata-db.data.generic-documents :as gdoc]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc
                                        delete as]]
   [cmr.oracle.connection :as oracle]
   [clj-time.coerce :as coerce]
   [cmr.common.date-time-parser :as dtp]
   ;[clojure.java.io :as io]
   )
  (:import
   (cmr.oracle.connection OracleStore)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities -- Prototype
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- dbresult->genericdoc
  "Converts a map result from the database to a generic doc map"
  [{:keys [id concept_id native_id provider_id document_name schema format
           mime_type metadata revision_id revision_date created_at deleted
           user_id transaction_id]} db]
  (cutil/remove-nil-keys {:id id
                          :concept-id concept_id
                          :native-id native_id
                          :provider-id provider_id
                          :document-name document_name
                          :schema schema
                          :format format ;; concepts convert this to mimetype in the get, but we already have mimetype
                          :mime-type mime_type
                          :metadata (when metadata (cutil/gzip-blob->string metadata))
                          :revision-id (int revision_id)
                          :revision-date (oracle/oracle-timestamp->str-time db revision_date)
                          :created-at (when created_at
                                        (oracle/oracle-timestamp->str-time db created_at))
                          :deleted (not= (int deleted) 0)
                          :user-id user_id
                          :transaction-id transaction_id}))

(defn- find-record
  "Look up latest revision of record in the db table and return a map of the row"
  [db provider-id doc-name]
  (-> db
      (jdbc/query ["SELECT * 
                    FROM cmr_generic_documents 
                    WHERE document_name = ? AND provider_id = ? 
                    ORDER BY revision_id ASC" doc-name provider-id])
      last))

(defn- get-next-id-seq
  "Get next ID for inserting new rows"
  [db]
  (-> db
      (jdbc/query ["SELECT METADATA_DB.cmr_generic_documents_seq.NEXTVAL FROM dual"])
      first
      :nextval
      long))

(defn- get-next-transaction-id
  "Get next transaction ID for inserting new rows"
  [db]
  (-> db
      (jdbc/query ["SELECT GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL FROM dual"])
      first
      :nextval
      long))

(defn- insert-record
  "Insert a row in the db table. This function expects the map form, not vector."
  [db row]
  (jdbc/insert! db :cmr_generic_documents row)
  (str (:concept_id row) ", revision #" (:revision_id row)))

(defn- strip-fields
  "Remove specified fields from map, in order to prepare for inserting into BLOB metadata field in database"
  [document fields-vec]
  (apply dissoc document fields-vec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-concept-id
  [db document]
  (let [{:keys [concept-type provider-id]} document
        raw-count (-> db
                      (jdbc/query ["SELECT count(DISTINCT concept_id) AS last FROM CMR_GENERIC_DOCUMENTS"])
                      first
                      :last)
        next (+ 1200000001 raw-count)]
    (format "X%s-%s" next provider-id)))

;; WORKS 
(defn save-concept
  "Create the document in the database, try to pull as many values out of the
   document as can be found. All documents must have at least a :Name and a
   :MetadataSpecification field."
  [db provider-id document]
  (let [rm-fields [:concept-id :concept-type :created-at :native-id :provider-id :revision-date :revision-id]
        metadata (json/generate-string (strip-fields document rm-fields))
        metadata-spec (get document "MetadataSpecification")
        schema (get metadata-spec "Name")
        version (get metadata-spec "Version")
        now (coerce/to-sql-time (dtp/parse-datetime (str (tkeep/now))))
        created-at (coerce/to-sql-time (dtp/parse-datetime (:created-at document)))
        revision-date (coerce/to-sql-time (dtp/parse-datetime (:revision-date document)))]
    (insert-record db
                   {:id (get-next-id-seq db)
                    :concept_id (:concept-id document)
                    :provider_id provider-id
                    :document_name (get document "Name")
                    :schema (get metadata-spec "Name")
                    :format (get metadata-spec "Name")
                    :mime_type (format "application/%s;version=%s" schema version)
                    :metadata (cutil/string->gzip-bytes metadata)
                    :revision_id (:revision-id document)
                    :revision_date revision-date
                    :created_at created-at
                    :deleted 0
                    :user_id "place-holder"
                    :transaction_id (get-next-transaction-id db)})))

(defn get-concepts
  [db concept-type provider concept-id-revision-id-tuples]
  (map #(dbresult->genericdoc % db)
       (jdbc/query db ["SELECT * FROM cmr_generic_documents WHERE format = ?" concept-type])))

;; WORKS
;; 1. this uses DESC in the sql, bc the update service uses 'first' (not 'last'), and i assumed that couldn't be chamged without breaking the in-memory side 
;; 2. is this supposed to be able to receive/return more than one concept id??
;; 3. why is it called "concept-id-revision-id-tuples" - seems to be vector of just concept-ids??
(defn get-latest-concepts
  [db concept-type provider concept-id-revision-id-tuples]
  (jdbc/with-db-transaction [transaction db]
                            (let [rows (jdbc/query transaction
                                                   ["SELECT * FROM cmr_generic_documents 
                                                WHERE concept_id = ? 
                                                ORDER BY revision_id DESC"
                                                    (first concept-id-revision-id-tuples)])]
                              (map #(dbresult->genericdoc % transaction) rows)))) 

;; WORKS
(defn get-concept
  [db concept-type provider concept-id]
  (jdbc/with-db-transaction [transaction db]
                            (let [rows (jdbc/query transaction
                                                   ["SELECT * 
                                                     FROM cmr_generic_documents 
                                                     WHERE concept_id = ?
                                                     ORDER BY revision_id DESC"
                                                    concept-id])]
                              (dbresult->genericdoc (first rows) transaction))))

;; Remove this??
(defn update-document
  "Add a new revision of the document in the database, only if at least one previous
   revision is found."
  [db document provider-id user-id doc-name schema]
  (when-let [last-revision (find-record db provider-id doc-name)]
    (let [parsed (json/parse-string document true)
          version (get-in parsed [:MetadataSpecification :Version])
          now (coerce/to-sql-time (dtp/parse-datetime (str (tkeep/now))))]
      (insert-record db 
                    {:id (get-next-id-seq db)
                     :concept_id (:concept_id last-revision)
                     :provider_id provider-id
                     :document_name doc-name
                     :schema schema
                     :format (identity schema)
                     :mime_type (format "application/%s;version=%s" schema version)
                     :metadata (cutil/string->gzip-bytes document)
                     :revision_id (-> (:revision_id last-revision)
                                      int
                                      inc)
                     :revision_date now
                     :created_at (:created_at last-revision)
                     :deleted 0
                     :user_id user-id
                     :transaction_id (get-next-transaction-id db)}))))

(defn force-delete
  [db concept-type provider concept-id revision-id])

(defn reset-all
  [db])

(def behaviour
  {:generate-concept-id generate-concept-id
   :save-concept save-concept
   :get-concept get-concept
   :get-concepts get-concepts
   :get-latest-concepts get-latest-concepts
   :update-document update-document
   :force-delete force-delete
   :reset reset-all})

(extend OracleStore
  gdoc/GenericDocsStore
  behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (def db (get-in user/system [:apps :metadata-db :db]))

  ;; io starts looking from the dev-system/ directory bc that's where the REPL starts
  (def test-file (slurp (clojure.java.io/resource "sample_tool.json")))
  (def gzip-blob (cutil/string->gzip-bytes test-file))
  (def test-file2 (slurp (clojure.java.io/resource "AllElements-V1.16.6.json"))) ;; invalid test -- lacks MetadataSpecification
  ;; here for your convenience - i didn't actually test with this, bc the transaction errors only show thru the api
  (def test-grid (slurp (clojure.java.io/resource "sample_grid.json")))
  ;;;;;;;; NEW

  (def my-rm-fields [:concept-id :concept-type :created-at :native-id :provider-id :revision-date :revision-id])
  (apply dissoc my-save my-rm-fields)


  ;; these work but using that first comment (def db) up there
  (jdbc/with-db-transaction [conn db] (get-concept conn :generic "PROV1" "X1200000002-PROV1"))

  ;;;;;;;; OLD

  (save-document db test-file "PROV1" "some-edl-user")

  ;; save-document
  (jdbc/insert! db
                :cmr_generic_documents
                ["id" "concept_id" "provider_id" "document_name" "schema" "format"
                 "mime_type" "metadata" "revision_id" "revision_date" "created_at" "deleted"
                 "user_id" "transaction_id"]
                [1 "myconceptid" "PROV1" "mydocname" "myschema" "myformat"
                 "application/json" gzip-blob 1 (cr/to-sql-time (p/parse-datetime "2020"))
                 (cr/to-sql-time (p/parse-datetime "2020")) 1 "myuserid" 1])

  (jdbc/with-db-transaction
    [conn db]
    (doall (get-document conn 1)))

  ;; get documents
  (jdbc/with-db-transaction
    [conn db]
    (jdbc/query db ["SELECT * FROM cmr_generic_documents"]))

  ;(concepts/db-result->concept-map "generic" db nil get-all-result)
  (jdbc/with-db-transaction [conn db] (get-documents conn "myformat"))

  ;; get document 
  (jdbc/with-db-transaction [conn db] (get-document conn 1))

  ;; update document
  (update-document db test-file "PROV1" "someotheruser2" "USGS_TOOLS_LATLONG" "umm-c")
  (def my-last-rev (last (jdbc/query db ["SELECT * 
                    FROM cmr_generic_documents 
                    WHERE document_name = ? AND provider_id = ? 
                    ORDER BY revision_id ASC" "USGS_TOOLS_LATLONG" "TESTPROV"])))

  ;; delete document
  (jdbc/delete! db "cmr_generic_documents" ["id=?" 1])
  )