(ns cmr.metadata-db.services.generic-documents
 "Buisness logic for Generic Documents"
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as coerce]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.log :as log :refer (debug info warn error trace)]
   [cmr.common.services.messages :as messages]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tkeeper]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.data.generic-documents :as data]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.provider-service :as provider-service]
   [cmr.metadata-db.services.util :as mdb-util]))

;; These are fields that should be put into the :Info field in the response
;; because they are generated by software. Add items to this list corrisponding
;; to database column names which are to be shared with the world.
(def info-fields [:concept-id :native-id :provider-id :document-name :schema
                  :format :mime-type :revision-id :revision-date :created-at
                  :user-id])

(defn- raw-generic->response
  "Take the raw database object returned from either SQL or In-Memory and shape
   it into the expected response by pulling out generated fields like concept-id
   and putting them in the :Info field, then putting the metadata inside the
   :Metadata field."
  [raw-doc]
  (let [metadata-field (:metadata raw-doc)
        metadata (if (map? metadata-field)
                   metadata-field
                   (json/parse-string metadata-field true))]
    {:Info (select-keys raw-doc info-fields) :Metadata metadata}))

(defn insert-generic-document
  "Insert a document under the provided provider-id. Generate a concept ID for
   the new record, At this time, nothing prevents multiple copies of a record
   from being inserted, users must know what they are doing. Update will not call
   this function."
  [context params provider-id raw-native-id document]
  (let [db (mdb-util/context->db context)
        ;; Validate that the provider exists, throws exeption.
        _ (provider-service/get-provider-by-id context provider-id true)
        document (if (map? document) (json/generate-string document) document)
        document-as-map (json/parse-string document true)
        native-id (or raw-native-id (.toString (java.util.UUID/randomUUID))) ;; can this stay?
        inner-metadata (json/parse-string (:metadata document-as-map) true)
        version (get-in inner-metadata [:MetadataSpecification :Version])
        doc-name (get inner-metadata :Name native-id)
        concept-type (get common-concepts/concept-prefix->concept-type (:concept-sub-type document-as-map))
        revision-id 1 ;; causes special behavor, a different status code will be returned
        document-add (assoc document-as-map
                            :provider-id (str provider-id)
                            :concept-type concept-type
                            :document-name (subs doc-name 0 (min 20 (count doc-name)))
                            :schema concept-type
                            :format (identity concept-type)
                            :mime-type (format "application/%s;version=%s"
                                               (name concept-type)
                                               version)
                            :revision-id revision-id ;; this function only for Create, not Update
                            :created-at (str (tkeeper/now))
                            :revision-date (dtp/clj-time->date-time-str (tkeeper/now))
                            :native-id native-id)
        concept-id (data/generate-concept-id db document-add)
        metadata (assoc document-add :concept-id concept-id)
        _ (data/save-concept db provider-id metadata)]
    (ingest-events/publish-event
     context
     (ingest-events/concept-update-event metadata))
    {:concept-id concept-id :revision-id revision-id}))

(defn read-generic-document
  "Return the latest record using the concept-id under the given provider"
  [context params provider-id native-id]
  (let [db (mdb-util/context->db context)
        provider {:provider-id provider-id}
        ;; TODO: Generic work: need to include concept type here
        concept-id (data/get-concept-id db :generic provider native-id)
        raw-doc (first (data/get-latest-concepts db :generic provider [concept-id]))]
    (if (nil? raw-doc)
      (messages/data-error :not-found str (format "Document [%s] was not found." native-id))
      (raw-generic->response raw-doc))))

(defn update-generic-document
  "Update a record which has already been inserted. Revision id and revision date
   will be automaticly handled. Old revisions will not be deleted."
  [context params provider-id native-id document]
  (let [db (mdb-util/context->db context)
        document (if (map? document) (json/generate-string document) document)
        document-map (json/parse-string document true)
        provider {:provider-id provider-id}
        concept-type (get common-concepts/concept-prefix->concept-type (:concept-sub-type document-map))
        concept-id (data/get-concept-id db concept-type provider native-id)
        latest-document (first (data/get-latest-concepts db :generic provider [concept-id]))
        latest-rev-id (:revision-id latest-document)
        revision-id (+ latest-rev-id 1)
        orig-native-id (:native-id latest-document)
        orig-concept-id (:concept-id latest-document)
        orig-create-date (:created-at latest-document)
        doc-name (:document-name latest-document)
        version (get-in document-map [:MetadataSpecification :Version])
        metadata (assoc document-map
                        :concept-type concept-type
                        :revision-id revision-id
                        :native-id orig-native-id
                        :concept-id orig-concept-id
                        :document-name doc-name
                        :schema concept-type
                        :format (identity concept-type)
                        :mime-type (format "application/%s;version=%s"
                                           (name concept-type)
                                           version)
                        :revision-date (dtp/clj-time->date-time-str (tkeeper/now))
                        :created-at orig-create-date)
        _ (data/save-concept db provider-id metadata)]
    (ingest-events/publish-event
     context
     (ingest-events/concept-update-event metadata))
    {:concept-id orig-concept-id :revision-id revision-id}))

;; TODO: Generic work: define a delete action
(defn delete-generic-document
  "Stub function, does nothing"
  [context params provider-id concept-id]
  (debug format "Delete stub function called with %s %s" provider-id concept-id)
  nil)
