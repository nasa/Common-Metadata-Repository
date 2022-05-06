(ns cmr.metadata-db.services.generic-documents
 "Buisness logic for Generic Documents"
  (:require
   [cheshire.core :as json]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tkeeper]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.metadata-db.data.generic-documents :as data]))

(defn insert-generic-document
  ""
  [context params provider-id document]
  (let [db (mdb-util/context->db context)
        document-map (json/parse-string document true)
        document-add (-> document-map
                         (assoc :provider-id (str provider-id))
                         (assoc :concept-type :generic)
                         (assoc :revision-id 1)
                         (assoc :native-id (.toString (java.util.UUID/randomUUID))))
        concept-id (data/generate-concept-id db document-add)
        metadata (assoc document-add :concept-id concept-id)
        result (data/save-concept db provider-id metadata)
        saved1 (data/get-concept db :generic {:provider-id provider-id} concept-id)
        saved (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id])]
    saved))

(defn read-generic-document
  [context params provider-id concept-id]
  (let [db (mdb-util/context->db context)
        doc (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id])]
    doc))

(defn update-generic-document
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
        saved (data/save-concept db provider-id metadata)
        result (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id])]
    ;(println saved ", " result)
    result))

(defn delete-generic-document
  [context params provider-id concept-id]
  {})