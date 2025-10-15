(ns cmr.ingest.api.generic-documents
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.config :as cfg]
   [cmr.common.generics :as gconfig]
   [cmr.common.log :refer [info]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.ingest.api.collections :as collections]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.api.services :as services]
   [cmr.ingest.api.tools :as tools]
   [cmr.ingest.api.variables :as variables]
   [cmr.ingest.validation.generic-document-validation :as generic-document-validation]
   [cmr.schema-validation.json-schema :as js-validater]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn string->stream
  "Used to construct request body using the metadata string
  retrieved from database when publishing a draft concept."
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

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
      (let [schema-path (format "schemas/%s/v%s/schema.json" (name schema) version)
            schema-obj (js-validater/parse-json-schema-from-path schema-path)]
        (if schema-obj
          (js-validater/validate-json schema-obj raw-json true)
          (errors/throw-service-error
           :invalid-data
           (format "While the [%s] schema with version [%s] is approved, it cannot be found." (util/html-escape schema) (util/html-escape version))))))))

(defn- concept-type->singular
  "Common task to convert concepts from their public URL form to their internal
   form. For example: grids -> grid"
  [route-params]
  (common-concepts/singularize-concept-type (:concept-type route-params)))

(def draft-concept->spec-map
  {:collection-draft :umm-c
   :service-draft :umm-s
   :tool-draft :umm-t
   :variable-draft :umm-var})

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
          spec-key (when (:Name specification)
                     (csk/->kebab-case-keyword (:Name specification)))
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
        ;; get the version in content-type and verify it.
        version-ct (when (and content-type
                              (string/includes? content-type ";version="))
                     (let [ct-split (string/split content-type #";version=")]
                       (if (= 2 (count ct-split))
                         (-> ct-split
                             last
                             string/trim)
                         (errors/throw-service-error
                          :invalid-data
                          "Missing version value in Content-Type"))))
        {:keys [spec-key spec-version document-name format]}
         (pull-metadata-specific-information request-context concept-type content-type raw-document)]
    ;; Check to see if the passed in record contains the MetadataSpecification/Name field and its
    ;; value matches that from the concept name in the route parameters.
    (if (or (not spec-key)
            (and (not= concept-type spec-key)
                 (not= (concept-type draft-concept->spec-map) spec-key)
                 (not= (common-concepts/get-concept-type-of-draft concept-type) spec-key)))
      (errors/throw-service-error
       :invalid-data
       (if-not spec-key
         "The MetadataSpecification schema element is missing from the record being ingested."
         (str spec-key " version " spec-version " are not supported")))
      (if (and version-ct
               (not= version-ct spec-version))
        (errors/throw-service-error
         :invalid-data
         (str "Version in MetadataSpecifcation [" spec-version "] is not the same as the one in Content-Type [" version-ct "]" ))
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
          :request-context request-context}))))

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

(defn validate-business-rules
  "Validates a concept against business rules defined in validation schemas."
  [context concept]
  (when concept
    (let [concept-type (:concept-type concept)]
      (when-not (common-concepts/is-draft-concept? concept-type)
        (generic-document-validation/validate-concept context concept)))))

(defn ingest-document
  "Ingest the concept into the database and the indexer through the database."
  [context concept headers]
  (info (format "Ingesting concept %s from client %s"
                (api-core/concept->loggable-string concept)
                (:client-id context)))
  (validate-business-rules context concept)
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
  "Deletes a generic document in elasticsearch. Creates a tombstone in the database for non-draft
   document. Delete all revisions from database for draft document.
   Returns a 201 if successful with the concept id and revision number for non-draft document.
   Returns a 200 if successful with the concept id and revision number for draft document.
   A 404 status is returned if the concept has already been deleted or removed from database."
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

(defn- extract-info-from-concept-id
  "Extract concept info from concept-id."
  [concept-id]
  (let [draft-concept-type (common-concepts/concept-id->type concept-id)
        provider-id (common-concepts/concept-id->provider-id concept-id)
        ;; get the concept type of the document that is contained in the draft
        ;; :collection is the concept type of the document that is contained in :collection-draft
        concept-type-in-draft (common-concepts/get-concept-type-of-draft draft-concept-type)]
    {:draft-concept-type draft-concept-type
     :provider-id provider-id
     :concept-type-in-draft concept-type-in-draft}))

(defn- get-info-from-metadata-db
  "Get information from metadata db."
  [request concept-id provider-id concept-type]
  (let [format-passed-in (:format request)
        context (:request-context request)
        search-result (mdb/get-latest-concept context concept-id)
        draft-native-id (:native-id search-result)
        metadata (:metadata search-result)
        ;; need to convert metadata to map and remove the mmt private data
        ;; then convert it back to json. Currently it only applies to variable-draft
        ;; publishing.
        metadata (if (= :variable concept-type)
                   (-> metadata
                       (json/parse-string)
                       (dissoc "_private")
                       (json/generate-string))
                   metadata)
        format (:format search-result)
        _ (when-not (and metadata format)
            (errors/throw-service-error
             :bad-request
             (format "Concept-id [%s] does not exist." concept-id)))
        content-type (if format-passed-in
                       format-passed-in
                       format)
        body (string->stream metadata)
        request (-> request
                    (assoc :body body :content-type content-type)
                    (assoc-in [:headers "content-type"] content-type)
                    (assoc-in [:route-params :provider-id] provider-id)
                    (assoc-in [:params :provider-id] provider-id)
                    (assoc-in [:route-params :concept-type] concept-type)
                    (assoc-in [:params :concept-type] concept-type))]
    {:native-id draft-native-id
     :request request}))

;; This function is dynamic to test publish-draft.
(defn- ^:dynamic publish-draft-concept
  "Publish a draft concept. i.e. Ingest the corresponding concept and delete the draft."
  ([request concept-id native-id]
   (publish-draft-concept request concept-id native-id nil nil))
  ([request concept-id native-id coll-concept-id coll-revision-id]
   (let [{:keys [draft-concept-type provider-id concept-type-in-draft]}
         (extract-info-from-concept-id concept-id)
         info (get-info-from-metadata-db request concept-id provider-id concept-type-in-draft)
         request (:request info)
         draft-native-id (:native-id info)
         ;;publish the concept-type-in-draft
         publish-result (case concept-type-in-draft
                          :collection (collections/ingest-collection provider-id native-id request)
                          :tool (tools/ingest-tool provider-id native-id request)
                          :service (services/ingest-service provider-id native-id request)
                          :variable (if coll-concept-id
                                      (variables/ingest-variable provider-id native-id request coll-concept-id coll-revision-id)
                                      (variables/ingest-variable provider-id native-id request))
                          (create-generic-document request))]
     (if (contains? #{200 201} (:status publish-result))
       ;;construct request to delete the draft.
       (let [delete-request (-> request
                                (assoc-in [:route-params :native-id] draft-native-id)
                                (assoc-in [:route-params :concept-type] draft-concept-type)
                                (assoc-in [:params :native-id] draft-native-id)
                                (assoc-in [:params :concept-type] draft-concept-type))
             delete-result (delete-generic-document delete-request)]
         (if (= 200 (:status delete-result))
           publish-result
           (errors/throw-service-error
            :bad-request
            (format "Publishing draft is successful with info [%s]. Deleting draft failed with info [%s]."
                    (:body publish-result) (:body delete-result)))))
       publish-result))))

;; This function is dynamic to test publish draft.
(defn- ^:dynamic read-body
  "Checks the content type and reads in the content from a socket stream."
  [content-type concept-id body]
  (if (= "application/x-www-form-urlencoded" content-type)
                       ;; this happens when the body is used but the content-type is not provided.
    (errors/throw-service-error
     :bad-request
     (format "To publish a draft [%s] with a body, a json Content-Type header needs to be provided." concept-id))
    (try
      (json/parse-string (string/trim (slurp body)))
      (catch Exception e
        (errors/throw-service-error
         :bad-request
         (format "The json body for publishing draft contains the following error [%s]." (.getMessage e)))))))

(defn publish-draft
  "Publish a draft concept, i.e. ingest the corresponding concept and delete the draft."
  [request concept-id native-id]
  (let [draft-concept-type (:draft-concept-type (extract-info-from-concept-id concept-id))
        content-type (:content-type request)
        body (:body request)
        body-map (when body
                   (read-body content-type concept-id body))
        ;; associate the "format" in the body to the request and use it to publish the draft. If the format is not provided,
        ;; in the body, the original format for the draft in the database will be used.
        request (assoc request :format (get body-map "format"))]
    (if (common-concepts/is-draft-concept? draft-concept-type)
      (if (= :variable-draft draft-concept-type)
        (let [coll-concept-id (get body-map "collection-concept-id")
              coll-revision-id (get body-map "collection-revision-id")]
          (if coll-concept-id
            (publish-draft-concept request concept-id native-id coll-concept-id coll-revision-id)
            ;; A variable draft does not have to be associated to a collection, if a collection concept id
            ;; does not exist, publish the variable anyway.
            (publish-draft-concept request concept-id native-id)))
        (publish-draft-concept request concept-id native-id))
      (errors/throw-service-error
       :bad-request
       (format "Only draft can be published in this route. concept-id [%s] does not belong to a draft concept" concept-id)))))

(defn crud-generic-document
  "This function checks for required parameters. If they don't exist then throw an error, otherwise send the request
   on to the corresponding function."
  [request funct-str]
  (case funct-str
    :create (create-generic-document request)
    :read (read-generic-document request)
    :update (update-generic-document request)
    :delete (delete-generic-document request)))
