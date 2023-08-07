(ns cmr.ingest.api.generic-documents
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.generics :as gconfig]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.config :as cfg]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.ingest.api.core :as api-core]
   [cmr.schema-validation.json-schema :as js-validater]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn disabled-for-ingest?
  "Determine if a generic schema is disabled for ingest
   Parameters:
   * schema, the keyword name of an approved generic"
  [schema]
  (some #{schema} (cfg/generic-ingest-disabled-list)))

(defn validate-json-against-schema
  "validate a document, returns an array of errors if there are problems
   Parameters:
   * schema, the keyword name of an approved generic
   * schema version, the schema version number, without 'v'"
  [schema version raw-json]
  (if-not (gconfig/approved-generic? schema version)
    (errors/throw-service-error
     :invalid-data
     (format "The [%s] schema on version [%s] is not an approved schema, this record cannot be ingested." (util/html-escape schema) (util/html-escape version)))
    (if (disabled-for-ingest? schema)
      (errors/throw-service-error
       :invalid-data
       (format "The %s schema is currently disabled and cannot be ingested." (util/html-escape schema)))
      (if-some [schema-file (gconfig/read-schema-specification schema version)]
        (let [schema-obj (js-validater/json-string->json-schema schema-file)]
          (js-validater/validate-json schema-obj raw-json true))
        (errors/throw-service-error
         :invalid-data
         (format "While the [%s] schema with version [%s] is approved, it cannot be found." (util/html-escape schema) (util/html-escape version)))))))

(defn- concept-type->singular
  "Common task to convert concepts from their public URL form to their internal
   form. For example: grids -> grid"
  [route-params]
  (common-concepts/singularize-concept-type (:concept-type route-params)))

(def draft-concept->spec-map
  {:collection-draft :umm-c
   :service-draft :umm-s
   :tool-draft :umm-t
   :variable-draft :umm-var
   :data-quality-summary-draft :data-quality-summary
   :order-option-draft :order-option})

(defn is-draft-concept?
  "This function checks to see if the concept to be ingested or updated
  is a draft concept."
  [request]
  (let [route-params (:route-params request)
        concept-type (concept-type->singular route-params)]
    (common-concepts/is-draft-concept? concept-type)))

(defn pull-metadata-specific-information
  "This function depending on the format of the document will get information
  needed to save the document to the database. Any supported record format is
  also supported for draft records."
  [context concept-type content-type raw-document]
  (if (string/includes? content-type "json")
    (let [document (json/parse-string raw-document true)
          specification (:MetadataSpecification document)
          spec-key (csk/->kebab-case-keyword (:Name specification ""))
          spec-version (:Version specification)
          document-name (or (:Name document)
                            (:ShortName document)
                            (when (common-concepts/is-draft-concept? concept-type) "Draft"))]
      {:spec-key spec-key
       :spec-version spec-version
       :document-name document-name
       :format (str "application/vnd.nasa.cmr.umm+json;version=" spec-version)})
    (if (common-concepts/is-draft-concept? concept-type)
      (let [draft-concept-type (common-concepts/get-concept-type-of-draft concept-type)
            sanitized-collection (spec/parse-metadata context draft-concept-type content-type raw-document)]
        {:spec-key concept-type
         :spec-version nil
         :format content-type
         :document-name (or (:ShortName sanitized-collection)
                            (:Name sanitized-collection)
                            "Draft")})
      {})))

(defn prepare-generic-document
  "Prepares a document to be ingested so that search can retrieve the contents.
   Throws exceptions if something goes wrong, returns a map otherwise."
  [request]
  (let [{:keys [route-params request-context headers params]} request
        provider-id (or (:provider params)
                        (:provider-id route-params))
        native-id (:native-id route-params)
        concept-type (concept-type->singular route-params)
        _ (lt-validation/validate-launchpad-token request-context)
        _ (api-core/verify-provider-exists request-context provider-id)
        _ (if-not (is-draft-concept? request)
            (acl/verify-ingest-management-permission
             request-context :update :provider-object provider-id)
            (acl/verify-provider-context-permission
             request-context :read :provider-object provider-id))
        raw-document (slurp (:body request))
        content-type (get headers "content-type")
        {:keys [spec-key spec-version document-name format]}
         (pull-metadata-specific-information request-context concept-type content-type raw-document)]
    (if (and (not= concept-type spec-key)
             (not= (concept-type draft-concept->spec-map) spec-key))
      (throw (UnsupportedOperationException. (format "%s version %s is not supported." spec-key spec-version)))
      {:concept (assoc {}
                       :metadata raw-document
                       :provider-id provider-id
                       :concept-type concept-type
                       :format format
                       :native-id native-id
                       :user-id (api-core/get-user-id request-context headers)
                       :extra-fields {:document-name document-name
                                      :schema spec-key})
       :spec-key spec-key
       :spec-version spec-version
       :provider-id provider-id
       :native-id native-id
       :request-context request-context})))

(defn validate-document-against-schema
  "This function will validate the passed in document with its schema and throw a
   service error if there is a validation error."
  [spec version metadata]
  (try
    (validate-json-against-schema spec version metadata)
    (catch org.everit.json.schema.ValidationException e
      (errors/throw-service-error
       :invalid-data
       (format (str "While validating the record against the [%s] schema with version [%s] "
                    "the following error occurred: [%s]. The record cannot be ingested.")
               (util/html-escape spec)
               (util/html-escape version)
               (.getMessage e))))))

(defn-timed save-document
  "Store a concept in mdb and indexer. Return concept-id, and revision-id."
  [context concept]
  (let [{:keys [concept-id revision-id]} (mdb/save-concept context concept)
        doc-name (get-in concept [:extra-fields :document-name])]
    {:name doc-name
     :concept-id concept-id
     :revision-id revision-id}))

(defn ingest-document
  "Ingest the concept into the database and the indexer through the database."
  [context concept headers]
  (info (format "Ingesting concept %s from client %s"
                (api-core/concept->loggable-string concept)
                (:client-id context)))
  (let [save-concept-result (save-document context concept)
        concept-to-log (-> concept
                           (api-core/concept-with-revision-id save-concept-result)
                           (assoc :name (:name save-concept-result)))]
    ;; Log the successful ingest, with the metadata size in bytes.
    (api-core/log-concept-with-metadata-size concept-to-log context)
    (api-core/generate-ingest-response headers
                                       (api-core/format-and-contextualize-warnings-existing-errors
                                        ;; name is added just for the logging above.
                                        ;; dissoc it so that it remains the same as the
                                        ;; original code.
                                        (dissoc save-concept-result :name)))))

(defn create-generic-document
  "Check a document for fitness to be ingested, and then ingest it. Records can
   be rejected for the following reasons:
   * unsupported schema
   * failed schema
   * failed validation rules (external) (pending)
   * Document name not unique"
  [request]
  (let [res (prepare-generic-document request)
        headers (:headers request)
        {:keys [spec-key spec-version request-context concept]} res
        metadata (:metadata concept)]
    (when-not (is-draft-concept? request)
      (validate-document-against-schema spec-key spec-version metadata))
    (ingest-document request-context concept headers)))

(defn read-generic-document
  "Read a document from the Native ID and return that document"
  [request]
  (let [{:keys [route-params request-context]} request
        provider-id (:provider-id route-params)
        native-id (:native-id route-params)
        concept-type (concept-type->singular route-params)
        query-params (assoc {} :provider-id provider-id :native-id native-id)]
    (mdb2/find-concepts request-context query-params concept-type {:raw? true})))

(defn update-generic-document
  "Update a generic document to the database and elastic search, return 204 and
   not the document because the user already has the document"
  [request]
  (let [res (prepare-generic-document request)
        headers (:headers request)
        {:keys [spec-key spec-version request-context concept]} res
        metadata (:metadata concept)]
    (when-not (is-draft-concept? request)
      (validate-document-against-schema spec-key spec-version metadata))
    (ingest-document request-context concept headers)))

(defn delete-generic-document
  "Deletes a generic document in elasticsearch and creates a tombstone in the database. Returns a 201
   if successful with the concept id and revision number. A 404 status is returned if the concept has
   already been deleted."
  [request]
  (let [{:keys [route-params request-context params]} request
        provider-id (or (:provider params)
                        (:provider-id route-params))
        native-id (:native-id route-params)
        concept-type (concept-type->singular route-params)
        _ (if-not (is-draft-concept? request)
            (acl/verify-ingest-management-permission
             request-context :update :provider-object provider-id)
            (acl/verify-provider-context-permission
             request-context :read :provider-object provider-id))]
    (api-core/delete-concept concept-type provider-id native-id request)))

(defn crud-generic-document
  "This function checks for required parameters. If they don't exist then throw an error, otherwise send the request
   on to the corresponding function."
  [request funct-str]
  (case funct-str
    :create (create-generic-document request)
    :read (read-generic-document request)
    :update (update-generic-document request)
    :delete (delete-generic-document request)))
