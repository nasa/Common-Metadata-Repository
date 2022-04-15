(ns cmr.metadata-db.data.oracle.generic-documents
  "Functions for saving, retrieving, deleting generic documents."
  (:require
   [clojure.java.jdbc :as j]
   [clojure.pprint :refer [pprint pp]]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.oracle.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.metadata-db.data.generic-documents :as gdoc]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc
                                        delete as]]
   [cmr.oracle.connection :as oracle]
   [clj-time.coerce :as cr]
   [cmr.common.date-time-parser :as p]
   [clojure.java.io :as io])
  (:import
   (cmr.oracle.connection OracleStore)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities -- Prototype
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; for inserting into BLOB field
(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

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
                          ;; these cause this error:
                          ;; ; Error printing return value at cmr.common.services.errors/internal-error! (errors.clj:61).
                          ;; ; Called db->oracle-conn with connection that was not within a db transaction. It must be called from within call j/with-db-transaction
                          ;; however, if you try to wrap them with j/with-db-transaction the repl BLOWS UP -- if you try it, wait for it after first error
                          :revision_date (oracle/oracle-timestamp->str-time db revision_date)
                          :created_at (when created_at
                                        (oracle/oracle-timestamp->str-time db created_at))
                          :deleted (not= (int deleted) 0)
                          :user_id user_id
                          :transaction_id transaction_id}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-document
  [db document]
  ;; see comment at bottom for example with super-dumb data
  ;; probably going to need to pull from concepts method
  )

;; not working -- see function above for reason why
(defn get-documents
  [db]
  (map #(dbresult->genericdoc % db)
       (j/query db ["SELECT * FROM cmr_generic_documents"])))

;; not working -- see function above for reason why
(defn get-document
  [db id]
  (first (map #(dbresult->genericdoc % db)
              (j/query db
                       [(str "SELECT *"
                             " FROM cmr_generic_documents"
                             " WHERE id = ?")
                        id]))))

(defn update-document
  [db document])

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
  (def my-file (io/file "../metadata-db-app/resources" "sample_tool.json"))
  (def bytes-blob (file->bytes my-file))

  ;; save-document
  (j/insert! db
             :cmr_generic_documents
             ["id" "concept_id" "native_id" "provider_id" "document_name" "schema" "format"
              "mime_type" "metadata" "revision_id" "revision_date" "created_at" "deleted"
              "user_id" "transaction_id"]
             [1 "myconceptid" "mynativeid" "PROV1" "mydocname" "myschema" "myformat"
              "application/json" bytes-blob 1 (cr/to-sql-time (p/parse-datetime "2020"))
              (cr/to-sql-time (p/parse-datetime "2020")) 1 "myuserid" 1])

  ;; get documents
  (def get-all-result (j/query db ["SELECT * FROM cmr_generic_documents"]))
  ;; as you can see, this doesn't work -- we can't just use this default -- but we might be able to extend our own method to this multi
  (concepts/db-result->concept-map "generic" db nil get-all-result)
  ;; here is the solution used in code above -- still needs work (see code above)
  (get-documents db)

  ;; get document -- still needs work (see code above)
  (get-document db 1)
  
  )