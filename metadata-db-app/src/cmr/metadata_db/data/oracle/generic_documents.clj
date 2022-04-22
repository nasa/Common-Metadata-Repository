(ns cmr.metadata-db.data.oracle.generic-documents
  "Functions for saving, retrieving, deleting generic documents."
  (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as jdbc]
   [clojure.pprint :refer [pprint pp]]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.time-keeper :as tkeep]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.oracle.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
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

(defn dbresult->genericdoc
  "Converts a map result from the database to a generic doc map"
  [{:keys [id concept_id native_id provider_id document_name schema format
           mime_type metadata revision_id revision_date created_at deleted
           user_id transaction_id]} db]
  (cutil/remove-nil-keys {:id id
                          :concept_id concept_id
                          :native_id native_id
                          :provider-id provider_id
                          :document_name document_name
                          :schema schema
                          :format format ;; concepts convert this to mimetype in the get, but we already have mimetype
                          :mime_type mime_type
                          :metadata (when metadata (cutil/gzip-blob->string metadata))
                          :revision_id (int revision_id)
                          :revision_date (oracle/oracle-timestamp->str-time db revision_date)
                          :created_at (when created_at
                                        (oracle/oracle-timestamp->str-time db created_at))
                          :deleted (not= (int deleted) 0)
                          :user_id user_id
                          :transaction_id transaction_id}))

(defn find-record
  "Look up latest revision of record in the db table and return a map of the row"
  [db provider-id doc-name]
  (-> db
      (jdbc/query ["SELECT * 
                    FROM cmr_generic_documents 
                    WHERE document_name = ? AND provider_id = ? 
                    ORDER BY revision_id ASC" doc-name provider-id])
      last)
  )

(defn get-next-id-seq
  "Get next ID for inserting new rows"
  [db]
  (-> db
      (jdbc/query ["SELECT METADATA_DB.cmr_generic_documents_seq.NEXTVAL FROM dual"])
      first
      :nextval
      long))

(defn get-next-transaction-id
  "Get next transaction ID for inserting new rows"
  [db]
  (-> db
      (jdbc/query ["SELECT GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL FROM dual"])
      first
      :nextval
      long))

(defn insert-record
  "Insert a row in the dn table"
  [db]
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-document
  "Create the document in the database, try to pull as many values out of the
   document as can be found. All documents must have at least a :Name and a
   :MetadataSpecification field."
  [db document provider-id user-id]

  (let [id-seq (-> db
                   (jdbc/query ["SELECT METADATA_DB.cmr_generic_documents_seq.NEXTVAL FROM dual"])
                   first
                   :nextval
                   long)
        transaction-id (-> db
                           (jdbc/query ["SELECT GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL FROM dual"])
                           first
                           :nextval
                           long)
        raw-count (-> db
                      (jdbc/query ["SELECT count(DISTINCT concept_id) AS last FROM CMR_GENERIC_DOCUMENTS"])
                      first
                      :last)
        next (+ 1200000001 raw-count)
        concept-id (format "X%s-%s" next provider-id)
        parsed (json/parse-string document true)
        document-name (:Name parsed)
        scheme (clojure.string/lower-case (get-in parsed [:MetadataSpecification :Name]))
        format-name scheme
        version (get-in parsed [:MetadataSpecification :Version])
        mime-type (format "application/%s;version=%s" scheme version)
        encoded-document (cutil/string->gzip-bytes document)
        now (coerce/to-sql-time (dtp/parse-datetime (str (tkeep/now))))]
    (jdbc/insert!
     db
     :cmr_generic_documents
     ["id"
      "concept_id"
      "provider_id"
      "document_name"
      "schema"
      "format"
      "mime_type"
      "metadata"
      "revision_id"
      "revision_date"
      "created_at"
      "deleted"
      "user_id"
      "transaction_id"]
     [id-seq
      concept-id
      provider-id
      document-name
      scheme
      format-name
      mime-type
      encoded-document
      1
      now
      now
      0
      user-id
      transaction-id])
    id-seq))

(defn get-documents
  [db format]
  (map #(dbresult->genericdoc % db)
       (jdbc/query db ["SELECT * FROM cmr_generic_documents WHERE format = ?" format])))

(defn get-document
  [db id]
  (first (map #(dbresult->genericdoc % db)
              (jdbc/query db
                          [(str "SELECT *"
                                " FROM cmr_generic_documents"
                                " WHERE id = ?")
                           id]))))

;; still needs to implement this requirement: All documents must have at least a :Name and a
;; :MetadataSpecification field.
(defn update-document
  "Add a new revision of the document in the database, only if at least one previous
   revision is found."
  [db document provider-id user-id doc-name schema]
  (when-let [last-revision (find-record db provider-id doc-name)]
    (let [parsed (json/parse-string document true)
          version (get-in parsed [:MetadataSpecification :Version])
          now (coerce/to-sql-time (dtp/parse-datetime (str (tkeep/now))))]
      (jdbc/insert! db 
                    :cmr_generic_documents
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
                     :transaction_id (get-next-transaction-id db)})
      doc-name)))

(defn delete-document
  [db document])

(defn reset-documents
  [db])

(def behaviour
  {:save-document save-document
   :get-documents get-documents
   :get-document get-document
   :update-document update-document
   :delete-document delete-document
   :reset-documents reset-documents})

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

  (save-document db test-file "TESTPROV" "some-edl-user")

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

  ;; get document -- still needs work (see code above)
  (jdbc/with-db-transaction [conn db] (get-document conn 1))

  ;; update document
  (update-document db test-file "TESTPROV" "someotheruser" "USGS_TOOLS_LATLONG" "umm-t")
  (def my-last-rev (last (jdbc/query db ["SELECT * 
                    FROM cmr_generic_documents 
                    WHERE document_name = ? AND provider_id = ? 
                    ORDER BY revision_id ASC" "USGS_TOOLS_LATLONG" "TESTPROV"])))

  ;; delete document
  (jdbc/delete! db "cmr_generic_documents" ["id=?" 1]))