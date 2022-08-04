(ns cmr.ingest.api.generic-documents
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as jio]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.generics :as gconfig]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
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
    ["Schema not approved"]
    (if-some [schema-url (jio/resource (format "generics/%s/v%s/schema.json"
                                               (name schema)
                                               version))]
      (let [schema-file (slurp schema-url)
            schema-obj (js-validater/json-string->json-schema schema-file)]
        (js-validater/validate-json schema-obj raw-json))
      ["Schema not found"])))

 (defn create-generic-document
   [request]
   "Check a document for fitness to be ingested, and then ingest it. Records can
   be rejected for the following reasons:
   * unsupported schema
   * failed schema
   * failed validation rules (external) (pending)"
   (println "Here in create-generic-document...")
   (let [{:keys [body route-params request-context]} request
         provider-id (:provider-id route-params)
         native-id (:native-id route-params)
         raw-document (slurp (:body request))
         document (json/parse-string raw-document true)
         specification (:MetadataSpecification document)
         spec-key (keyword (string/lower-case (:Name specification)))
         spec-version (:Version specification)]
     (if-some [validation-errors (validate-json-against-schema spec-key spec-version raw-document)]
       validation-errors
       (let [result (tgen/create-generic request-context [provider-id native-id] raw-document)]
         result))))

 (defn read-generic-document
   [request]
   "Read a document from it's Native_Id and return it"
   (let [{:keys [route-params request-context]} request
         provider-id (:provider-id route-params)
         native-id (:native-id route-params)
       ;; The update-generic is a macro which allows for a list of URL parameters to be
       ;; passed in to be resolved by a function.
        response (tgen/read-generic request-context [provider-id native-id])
         document (:body response)]
     {:status 200 :body document}))

 (defn update-generic-document
   [request]
   (let [{:keys [body :route-params request-context]} request
         ; TODO: Generic work - add token check
         provider-id (:provider-id route-params)
         native-id (:native-id route-params)
         raw-document (slurp (:body request))
         document (json/parse-string raw-document true) ; is this type always a map?
         specification (:MetadataSpecification document)
         spec-key (keyword (string/lower-case (:Name specification)))
         spec-version (:Version specification)]
     (if-some [validation-errors (validate-json-against-schema spec-key spec-version raw-document)]
       validation-errors
       ;; The update-generic is a macro which allows for a list of URL parameters to be
       ;; passed in to be resolved by a function.
       (let [result (tgen/update-generic request-context [provider-id native-id] raw-document)]
         {:status 204}))))

 (defn delete-generic-document [request]
   (println "stub function: delete " request))
