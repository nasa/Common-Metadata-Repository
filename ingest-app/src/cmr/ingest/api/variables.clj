(ns cmr.ingest.api.variables
  "Variable ingest functions in support of the ingest API."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]))

(defn- validate-and-prepare-variable-concept
  "Validate variable concept, set the concept format and returns the concept;
  throws error if the metadata is not a valid against the UMM variable JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :variable))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn ingest-variable
  "Processes a request to create or update a variable."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        concept (api-core/body->concept!
                 :variable provider-id native-id body content-type headers)]
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission
      request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (validate-and-prepare-variable-concept concept)]
      (->> (api-core/set-user-id concept request-context headers)
           (ingest/save-variable request-context)
           (api-core/generate-ingest-response headers)))))

(defn delete-variable
  "Deletes the variable with the given variable-key."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (acl/verify-ingest-management-permission
     request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (api-core/generate-ingest-response
     headers
     (ingest/delete-variable request-context provider-id native-id))))
