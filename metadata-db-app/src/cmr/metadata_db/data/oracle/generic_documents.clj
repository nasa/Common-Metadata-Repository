(ns cmr.metadata-db.data.oracle.generic-documents
  "Functions for saving, retrieving, deleting generic documents."
  (:require
   [clojure.java.jdbc :as j]
   [clojure.pprint :refer [pprint pp]]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.metadata-db.data.oracle.sql-helper :as sh]
   [cmr.metadata-db.data.generic-documents :as gdoc]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc
                                        delete as]]
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

(defn save-document
  [db document]
  ;; see comment at bottom
  )

(defn get-documents
  [db])

(defn get-document
  [db document-id])

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


  )