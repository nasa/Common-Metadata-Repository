(ns cmr.metadata-db.services.generic-documents
 "Buisness logic for Generic Documents"
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as coerce]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tkeeper]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.data.generic-documents :as data]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.services.util :as mdb-util]))

(defn- raw-generic->response
  "Take the raw database object returned from either SQL or In-Memory and shape
   it into the expected response"
  [raw-doc]
  (let [concept-id (:concept-id raw-doc)
        provider-id (:provider-id raw-doc)
        revision-id (:revision-id raw-doc)
        metadata (-> raw-doc
                     (dissoc :concept-id)
                     (dissoc :concept-type)
                     (dissoc :native-id)
                     (dissoc :provider-id)
                     (dissoc :revision-id))]
    {:concept-id concept-id :provider-id provider-id :revision-id revision-id :metadata metadata}))

;; TODO: Generic work: We need to be able to handle multiple revisions - right now every revision is 1 
;; and we create a new document, instead of not letting them. - or maybe that is the design difference ;; between POST and PUT.
;(defn- set-or-generate-revision-id
;  "Get the next available revision id from the DB for the given concept or
;  one if the concept has never been saved."
;  [db provider concept & previous-revision]
;  (if (:revision-id concept)
;    concept
;    (let [{:keys [concept-id concept-type provider-id]} concept
;          previous-revision (first previous-revision)
;          existing-revision-id (:revision-id (or previous-revision
;                                                 (c/get-concept db concept-type provider concept-id)))
;          revision-id (if existing-revision-id (inc existing-revision-id) 1)]
;      (assoc concept :revision-id revision-id))))

(defn insert-generic-document
  "Insert a document under the provided provider-id. Generate a concept ID for the new record,
   At this time, nothing prevents multiple copies of a record from being inserted, users must
   know what they are doing."
  [context params provider-id document]
  (let [db (mdb-util/context->db context)
        ;; I am now passing it in as edn;(json/parse-string document)
        document-add (-> document
                         (assoc :provider-id (str provider-id))
                         (assoc :concept-type :generic)
                         ;; TODO: Generic work: Can't hard code the revision-id
                         (assoc :revision-id 1)
                         (assoc :created-at (str (tkeeper/now))) ;; make sure i dont break in-mem side
                         (assoc :revision-date (dtp/clj-time->date-time-str (tkeeper/now))) ;; make sure i dont break in-mem side
                         (assoc :native-id (.toString (java.util.UUID/randomUUID))))
        concept-id (data/generate-concept-id db document-add)
        metadata (dissoc document-add "concept-sub-type")
        metadata (assoc metadata :concept-id concept-id)
        _ (data/save-concept db provider-id metadata)
        ;; TODO: Generic work: I think this is going to cause a race condition! We should return the actual thing that was saved and not get it back.
        saved (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))]
    (ingest-events/publish-event
      context
      (ingest-events/concept-update-event metadata))
    (raw-generic->response saved)))

(defn read-generic-document
  "Return the lattest record using the concept-id under the given provider"
  [context params provider-id concept-id]
  (let [db (mdb-util/context->db context)
        raw-doc (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))]
    (raw-generic->response raw-doc)))

(defn update-generic-document
  "Update a record which has already been inserted. Revision id and revision date
   will be automaticly handled. Old revisions will not be deleted."
  [context params provider-id concept-id document]
  (let [db (mdb-util/context->db context)
        document-map (json/parse-string document true)
        latest-document (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))
        latest-rev-id (:revision-id latest-document)
        orig-native-id (:native-id latest-document)
        orig-concept-id (:concept-id latest-document)
        orig-create-date (:created-at latest-document)
        metadata (-> document-map
                     (assoc :concept-type :generic)
                     (assoc :revision-id (+ latest-rev-id 1))
                     (assoc :native-id orig-native-id)
                     (assoc :concept-id orig-concept-id)
                     (assoc :revision-date (dtp/clj-time->date-time-str (tkeeper/now)))
                     (assoc :created-at orig-create-date))
        _ (data/save-concept db provider-id metadata)
        updated (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))]
    (ingest-events/publish-event
     context
     (ingest-events/concept-update-event metadata))
    updated))

(defn delete-generic-document
  "Stub function, does nothing"
  [context params provider-id concept-id]
  {})