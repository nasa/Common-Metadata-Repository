(ns cmr.metadata-db.services.generic-documents
 "Buisness logic for Generic Documents"
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as coerce]
   ;[cmr.common.concepts :as common-concepts]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.services.messages :as messages]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tkeeper]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.data.generic-documents :as data]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.services.util :as mdb-util]))

(defn- assoc-these-from
  "Creates a new map from a given map where only the fields listed in items are included"
  [items data]
  (cutil/remove-nil-keys (reduce #(assoc %1 %2 (get data %2)) {} items)))

;; These are fields that should be put into the :Info field in the response
;; because they are generated by software. Add items to this list corrisponding
;; to database column names which are to be shared with the world.
(def info-fields [:concept-id :native-id :provider-id :document-name :schema
                  :format :mime-type :revision-id :revision-date :created-at
                  :user-id])

;; These are fields that should not be inclucded in the :Metadata field because
;; they are generated by software. Add items to this list corrisponding to 
;; database column names which should NOT be shared with the world and are not
;; in the info-fields list above.
(def fields-to-drop (into [] (concat info-fields [:id :deleted :transaction-id])))

(defn- raw-generic->response
  "Take the raw database object returned from either SQL or In-Memory and shape
   it into the expected response by pulling out generated fields like concept-id
   and putting them in the :Info field, then putting the metadata inside the
   :Metadata field without any of these extra fields."
  [raw-doc]
  {:Info (assoc-these-from info-fields raw-doc)
   :Metadata (reduce #(dissoc %1 %2) raw-doc fields-to-drop)})

(defn- create-error-map
  ([error-id error-messsage]
   (create-error-map error-id error-messsage nil))
  ([error-id error-messsage extra]
   (merge {:error error-id :error-message error-messsage} extra)))

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
  "Insert a document under the provided provider-id. Generate a concept ID for
   the new record, At this time, nothing prevents multiple copies of a record
   from being inserted, users must know what they are doing."
  [context params provider-id raw-native-id document]
  (let [db (mdb-util/context->db context)
        native-id (or raw-native-id (.toString (java.util.UUID/randomUUID))) ;; can this stay?
        ;; I am now passing it in as edn;(json/parse-string document)
        document-add (assoc document
                            :provider-id (str provider-id)
                            :concept-type :generic
                            ;; TODO: Generic work: Can't hard code the revision-id - or is this
                            ;; because POST and PUT call different funtions.
                            :revision-id 1
                            :created-at (str (tkeeper/now))
                            :revision-date (dtp/clj-time->date-time-str (tkeeper/now))
                            :native-id native-id)
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
  [context params provider-id native-id]
  (let [db (mdb-util/context->db context)
        provider {:provider-id provider-id}
        concept-id (data/get-concept-id db :generic provider native-id)
        _ (println "concept-id:" concept-id)
        raw-doc (first (data/get-latest-concepts db :generic provider [concept-id]))]
    (if (nil? raw-doc)
        (messages/data-error :not-found str (format "Document [%s] was not found." native-id))
        (raw-generic->response raw-doc))))

(defn update-generic-document
  "Update a record which has already been inserted. Revision id and revision date
   will be automaticly handled. Old revisions will not be deleted."
  [context params provider-id native-id document]
  (let [db (mdb-util/context->db context)
        document-map (json/parse-string document true)
        provider {:provider-id provider-id}
        concept-id (data/get-concept-id db :generic provider native-id)
        latest-document (first (data/get-latest-concepts db :generic provider [concept-id]))
        latest-rev-id (:revision-id latest-document)
        orig-native-id (:native-id latest-document)
        orig-concept-id (:concept-id latest-document)
        orig-create-date (:created-at latest-document)
        metadata (assoc document-map
                     :concept-type :generic
                     :revision-id (+ latest-rev-id 1)
                     :native-id orig-native-id
                     :concept-id orig-concept-id
                     :revision-date (dtp/clj-time->date-time-str (tkeeper/now))
                     :created-at orig-create-date)
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
