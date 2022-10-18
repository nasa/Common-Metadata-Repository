(ns cmr.ingest.api.generic-documents
  "Subscription ingest functions in support of the ingest API."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as jio]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.generics :as gconfig]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [defn-timed]]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.messages :as messages]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.schema-validation.json-schema :as js-validater]))

(defn validate-json-against-schema
  "validate a document, returns an array of errors if there are problems
   Parameters:
   * schema, the keyword name of an approved generic
   * schema version, the schema version number, without 'v'"
  [schema version raw-json]
  (if-not (gconfig/approved-generic? schema version)
    (errors/throw-service-error
     :invalid-data
     (format "The [%s] schema on version [%s] is not an approved schema. This record cannot be ingested." schema version))
    (if-some [schema-url (jio/resource (format "schemas/%s/v%s/schema.json"
                                               (name schema)
                                               version))]
      (let [schema-file (slurp schema-url)
            schema-obj (js-validater/json-string->json-schema schema-file)]
        (js-validater/validate-json schema-obj raw-json true))
      (errors/throw-service-error
       :invalid-data
       (format "While the [%s] schema with version [%s] is approved, it cannot be found." schema version)))))

(defn get-sub-concept-type-concept-id-prefix
  "There are many concept types within generics. Read in the concept-id prefix for this specific one."
  [spec-key version]
  (if-some [index-url (jio/resource (format "schemas/%s/v%s/index.json"
                                            (name spec-key)
                                            version))]
    (let [index-file-str (slurp index-url)
          index-file (json/parse-string index-file-str true)
          index-sub-concept (:SubConceptType index-file)]
      (if index-sub-concept
        index-sub-concept
        (:generic (set/map-invert common-concepts/concept-prefix->concept-type))))
    (:generic (set/map-invert common-concepts/concept-prefix->concept-type))))

(def required-query-parameters
  "This defines in a map required parameters that are passed in and where they would be
   located once the http request makes its way through compojure. If there is an error the error
   messages goes into the cmr.ingest.services.messages.clj file."
  {:provider messages/provider-does-not-exist})

;; TODO: Generic work: This could be a candidate for a configuration file.
(defn validate-required-parameter
  "This function validates that the required parameters are present. If not then throw a service exception to let
  the end users know what to do."
  [required-param-to-check request]
  (let [param-key (first required-param-to-check)
        msg (second required-param-to-check)
        value (get-in request [:params param-key])]
    (when-not value
      (errors/throw-service-error :invalid-data (msg)))))

(defn validate-any-required-query-parameters
  [request required-parameters]
  (doseq [param required-parameters]
    (validate-required-parameter param request)))

(defn prepare-generic-document
  "Prepares a document to be ingested so that search can retrieve the contents.
   Throws exceptions if something goes wrong, returns a map otherwise."
  [request]
  (let [{:keys [route-params request-context headers params]} request
        provider-id (or (:provider params)
                        (:provider-id route-params))
        native-id (:native-id route-params)
        concept-type (keyword (:concept-type route-params))
        _ (lt-validation/validate-launchpad-token request-context)
        _ (api-core/verify-provider-exists request-context provider-id)
        _ (acl/verify-ingest-management-permission
           request-context :update :provider-object provider-id)
        raw-document (slurp (:body request))
        document (json/parse-string raw-document true)
        specification (:MetadataSpecification document)
        spec-key (keyword (string/lower-case (:Name specification)))
        spec-version (:Version specification)
        concept-sub-type (get-sub-concept-type-concept-id-prefix spec-key spec-version)]
    (if (not= concept-type spec-key)
      (throw UnsupportedOperationException)
      {:concept (assoc {}
                       :metadata raw-document
                       :provider-id provider-id
                       :concept-type concept-type
                       :format (str "application/vnd.nasa.cmr.umm+json;version=" spec-version)
                       :native-id native-id
                       :user-id (api-core/get-user-id request-context headers)
                       :extra-fields {:document-name (:Name document)
                                      :schema spec-key}
                       :concept-sub-type concept-sub-type)
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
               spec
               version
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
  (info (format "Ingesting collection %s from client %s"
                (api-core/concept->loggable-string concept)
                (:client-id context)))
  (let [save-collection-result (save-document context concept)
        concept-to-log (-> concept
                           (api-core/concept-with-revision-id save-collection-result)
                           (assoc :name (:name save-collection-result)))]
    ;; Log the successful ingest, with the metadata size in bytes.
    (api-core/log-concept-with-metadata-size concept-to-log context)
    (api-core/generate-ingest-response headers
                                       (api-core/format-and-contextualize-warnings-existing-errors
                                        ;; name is added just for the logging above.
                                        ;; dissoc it so that it remains the same as the
                                        ;; original code.
                                        (dissoc save-collection-result :name)))))

(defn create-generic-document
  [request]
  "Check a document for fitness to be ingested, and then ingest it. Records can
   be rejected for the following reasons:
   * unsupported schema
   * failed schema
   * failed validation rules (external) (pending)
   * Document name not unique"
  (let [res (prepare-generic-document request)
        headers (:headers request)
        {:keys [spec-key spec-version provider-id native-id request-context concept]} res
        metadata (:metadata concept)
        metadata-json (json/generate-string concept)]
    (validate-document-against-schema spec-key spec-version metadata)
    (ingest-document request-context concept headers)))

(defn read-generic-document
  [request]
  "Read a document from the Native ID and return that document"
  (let [{:keys [route-params request-context params]} request
        provider-id (:provider params)
        native-id (:native-id route-params)
        concept-type (keyword (:concept-type route-params))
        query-params (assoc {} :provider-id provider-id :native-id native-id)]
    (mdb2/find-concepts request-context query-params concept-type {:raw? true})))

(defn update-generic-document
  [request]
  "Update a generic document to the database and elastic search, return 204 and
   not the document because the user already has the document"
  (let [res (prepare-generic-document request)
        headers (:headers request)
        {:keys [spec-key spec-version provider-id native-id request-context concept]} res
        metadata (:metadata concept)]
    (validate-document-against-schema spec-key spec-version metadata)
    (ingest-document request-context concept headers)))

(defn delete-generic-document
  "Deletes a generic document in elasticsearch and creates a tombstone in the database. Returns a 201
   if successful with the concept id and revision number. A 404 status is returned if the concept has
   already been deleted."
  [request]
  (let [{:keys [route-params request-context headers params]} request
        provider-id (or (:provider params)
                        (:provider-id route-params))
        native-id (:native-id route-params)
        concept-type (:concept-type route-params)]
    (api-core/delete-concept concept-type provider-id native-id request)))

(defn validate-required-query-parameters
  "This function checks for required parameters. If they don't exist then throw an error, otherwise send the request
   on to the corresponding function."
  [request funct-str]
  (validate-any-required-query-parameters request required-query-parameters)
  (case funct-str
    :create (create-generic-document request)
    :read (read-generic-document request)
    :update (update-generic-document request)
    :delete (delete-generic-document request)))
