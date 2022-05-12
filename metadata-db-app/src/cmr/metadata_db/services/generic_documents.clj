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
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.metadata-db.data.generic-documents :as data]))

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

(defn insert-generic-document
  "Insert a document under the provided provider-id. Generate a concept ID for the new record,
   At this time, nothing prevents multiple copies of a record from being inserted, users must
   know what they are doing."
  [context params provider-id document]
  (let [db (mdb-util/context->db context)
        document-as-map (json/parse-string document true)
        document-add (-> document-as-map
                         (assoc :provider-id (str provider-id))
                         (assoc :concept-type :generic)
                         (assoc :revision-id 1)
                         (assoc :created-at (str (tkeeper/now))) ;; make sure i dont break in-mem side
                         (assoc :revision-date (dtp/clj-time->date-time-str (tkeeper/now))) ;; make sure i dont break in-mem side
                         (assoc :native-id (.toString (java.util.UUID/randomUUID))))
        concept-id (data/generate-concept-id db document-add)
        metadata (assoc document-add :concept-id concept-id)
        _ (data/save-concept db provider-id metadata)
        saved (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))]
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
    updated))

(defn delete-generic-document
  "Stub function, does nothing"
  [context params provider-id concept-id]
  {})