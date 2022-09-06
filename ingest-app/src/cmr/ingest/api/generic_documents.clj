(ns cmr.ingest.api.generic-documents
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as jio]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.generics :as gconfig]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.messages :as messages]
   [cmr.ingest.services.subscriptions-helper :as jobs]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.transmit.search :as search]
   [cmr.transmit.urs :as urs]
   [cmr.transmit.generic-documents :as tgen]
   [cmr.schema-validation.json-schema :as js-validater])
  (:import
   [java.util UUID]))

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
        concept-type (keyword (:concept-sub-type route-params))
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
      {:concept (assoc {} :metadata raw-document
                       :provider-id provider-id
                       :format (str "application/vnd.nasa.cmr.umm+json;version=" spec-version)
                       :native-id native-id
                       :user-id (api-core/get-user-id request-context headers)
                       :extra-fields {}
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
    (api-core/generate-ingest-response
     headers
     (:body (tgen/create-generic request-context [provider-id native-id] metadata-json)))))

(defn read-generic-document
  [request]
  "Read a document from the Native ID and return that document"
  (let [{:keys [route-params request-context params]} request
        provider-id (or (:provider params)
                        (:provider-id route-params))
        native-id (:native-id route-params)]
    (tgen/read-generic request-context [provider-id native-id])))

(defn update-generic-document
  [request]
  "Update a generic document to the database and elastic search, return 204 and
   not the document because the user already has the document"
  (let [res (prepare-generic-document request)
        headers (:headers request)
        {:keys [spec-key spec-version provider-id native-id request-context concept]} res
        metadata (:metadata concept)
        metadata-json (json/generate-string concept)]
    (validate-document-against-schema spec-key spec-version metadata)
    ;; The update-generic is a macro which allows for a list of URL parameters to be
    ;; passed in to be resolved by a function.
    (api-core/generate-ingest-response
     headers
     (:body (tgen/update-generic request-context [provider-id native-id] metadata-json)))))

(defn delete-generic-document
  "Deletes a generic document in elasticsearch and creates a tombstone in the database. Returns a 201
   if successful with the concept id and revision number. A 404 status is returned if the concept has
   already been deleted."
  [request]
  (let [{:keys [route-params request-context headers params]} request
        provider-id (or (:provider params)
                        (:provider-id route-params))
        native-id (:native-id route-params)
        concept-type (:concept-sub-type route-params)]
    (api-core/delete-concept concept-type provider-id native-id request)))

;; TODO: Generic work:  Once we decide on the actual API calls we need to put the provider parameter either in the :param or :route-param
;; sections of the request - which ever is appropriate for the URL.  Right now we are putting it into the route-params which is where
;; the prepare-generic-document function is looking for it.
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
