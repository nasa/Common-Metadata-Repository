(ns cmr.metadata-db.data.oracle.generic-documents
  "Functions for saving, retrieving, deleting generic documents."
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as coerce]
   [clojure.java.jdbc :as jdbc]
   [clojure.pprint :refer [pprint pp]]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.time-keeper :as tkeep]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.generic-documents :as gdoc]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.oracle.connection :as oracle]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with
                                        order-by desc delete as]])
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
                          :revision-id (int revision_id)
                          :revision-date (oracle/oracle-timestamp->str-time db revision_date)
                          :created-at (when created_at
                                        (oracle/oracle-timestamp->str-time db created_at))
                          :deleted (not= (int deleted) 0)
                          :user-id user_id
                          :transaction-id transaction_id}
                          :metadata (when metadata (json/parse-string (cutil/gzip-blob->string metadata) true))))

(defn- find-record
  "Look up latest revision of record in the db table and return a map of the row"
  [db provider-id native-id]
  (-> db
      (jdbc/query ["SELECT * 
                    FROM cmr_generic_documents 
                    WHERE native_id = ? AND provider_id = ? 
                    ORDER BY revision_id ASC" native-id provider-id])
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
;; TODO: Generic work: shouldn't we use the same number generator as cmr.metadata-db.data.oracle.concepts/ generate-concept-id
;; seq-num (:nextval (first (su/query db ["SELECT concept_id_seq.NEXTVAL FROM DUAL"])))
(defn generate-concept-id
  "create the next concept id using the count of records currently in the database"
  [db document]
  (let [{:keys [concept-type provider-id]} document
        concept-sub-type (get document :concept-sub-type)
        raw-count (-> db
                      (jdbc/query ["SELECT count(DISTINCT concept_id) AS last FROM CMR_GENERIC_DOCUMENTS"])
                      first
                      :last)
        next (+ 1200000001 raw-count)]
    (if (some? concept-sub-type)
      (common-concepts/build-generic-concept-id {:concept-type concept-sub-type
                                                 :provider-id provider-id
                                                 :sequence-number (biginteger next)})
      (common-concepts/build-concept-id {:concept-type concept-type
                                         :provider-id provider-id
                                         :sequence-number (biginteger next)}))))

(defn get-concept-id
  "Convert a native-id to a concept-id"
  [db concept-type provider native-id]
  ;; sql will return a map of the column name and value, strip the first value out
  (-> db
      (jdbc/query [(str "SELECT concept_id "
                        "FROM cmr_generic_documents "
                        "WHERE provider_id = ? AND native_id = ?")
                   (:provider-id provider) native-id])
      first
      vals
      first))

(defn save-concept
  "Create the document in the database, try to pull as many values out of the
   document as can be found. All documents must have at least a :Name and a
   :MetadataSpecification field."
  [db provider-id document]
  (let [rm-fields [:deleted :concept-id :concept-type :created-at :native-id :provider-id :revision-date :revision-id]
        metadata (json/generate-string (strip-fields document rm-fields))
        metadata-spec (get document :MetadataSpecification)
        schema (get metadata-spec :Name)
        version (get metadata-spec :Version)
        created-at (coerce/to-sql-time (dtp/parse-datetime (:created-at document)))
        revision-date (coerce/to-sql-time (dtp/parse-datetime (:revision-date document)))]
    (insert-record db
                   {:id (get-next-id-seq db)
                    :concept_id (:concept-id document)
                    :native_id (:native-id document)
                    :provider_id provider-id
                    :document_name (get document :Name)
                    :schema schema
                    :format schema
                    :mime_type (format "application/%s;version=%s" schema version)
                    :metadata (cutil/string->gzip-bytes metadata)
                    :revision_id (:revision-id document)
                    :revision_date revision-date
                    :created_at created-at
                    :deleted (or (:deleted document) 0)
                    :user_id "place-holder"
                    :transaction_id (get-next-transaction-id db)})))

(defn get-concepts
  "Return all the concepts for a given format"
  [db concept-type provider concept-id-revision-id-tuples]
  (map #(dbresult->genericdoc % db)
       (jdbc/query db ["SELECT * FROM cmr_generic_documents WHERE format = ?" concept-type])))

;; WORKS
;; 1. this uses DESC in the sql, bc the update service uses 'first' (not 'last'), and i assumed that couldn't be chamged without breaking the in-memory side 
;; 2. is this supposed to be able to receive/return more than one concept id??
;; 3. why is it called "concept-id-revision-id-tuples" - seems to be vector of just concept-ids??
(defn get-latest-concepts
  [db concept-type provider concept-id-revision-id-tuples]
  (jdbc/with-db-transaction
   [transaction db]
   (let [rows (jdbc/query transaction
                          ["SELECT * FROM cmr_generic_documents
                            WHERE concept_id = ?
                            ORDER BY revision_id DESC"
                           (first concept-id-revision-id-tuples)])]
     (map #(dbresult->genericdoc % transaction) rows)))) 

(defn get-concept
  "Return either the lattest generic document or a specific revision"
  ([db concept-type provider concept-id rev-id]
      (jdbc/with-db-transaction
        [transaction db]
        (let [rows (jdbc/query transaction
                               ["SELECT *
                                 FROM cmr_generic_documents
                                 WHERE concept_id = ? AND revision_id = ?"
                                concept-id, rev-id])]
          (dbresult->genericdoc (first rows) transaction))))
  ([db concept-type provider concept-id]
   (jdbc/with-db-transaction
     [transaction db]
     (let [rows (jdbc/query transaction
                            ["SELECT *
                              FROM cmr_generic_documents
                              WHERE concept_id = ?
                              ORDER BY revision_id DESC"
                             concept-id])]
       (dbresult->genericdoc (first rows) transaction)))))

;; Remove this?? API does not call it
(defn update-document
  "Add a new revision of the document in the database, only if at least one previous
   revision is found."
  ([db document provider-id user-id native-id schema]
   (update-document db document provider-id user-id native-id schema 0))
  ([db document provider-id user-id native-id schema delete-it]
   (when-let [last-doc-revision (find-record db provider-id native-id)]
     (let [parsed (json/parse-string document true)
           version (get-in parsed [:MetadataSpecification :Version])
           now (coerce/to-sql-time (dtp/parse-datetime (str (tkeep/now))))]
       (insert-record db
                      {:id (get-next-id-seq db)
                       :concept_id (:concept_id last-doc-revision)
                       :native_id (:native_id last-doc-revision)
                       :provider_id provider-id
                       :document_name (:name last-doc-revision)
                       :schema schema
                       :format (identity schema)
                       :mime_type (format "application/%s;version=%s" schema version)
                       :metadata (cutil/string->gzip-bytes document)
                       :revision_id (-> (:revision_id last-doc-revision)
                                        int
                                        inc)
                       :revision_date now
                       :created_at (:created_at last-doc-revision)
                       :deleted delete-it
                       :user_id user-id
                       :transaction_id (get-next-transaction-id db)})))))

(defn force-delete
  [db concept-type provider concept-id revision-id])

(defn reset-all
  [db])

(def behaviour
  {:generate-concept-id generate-concept-id
   :get-concept-id get-concept-id
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
