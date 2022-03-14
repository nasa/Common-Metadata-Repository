(ns cmr.ingest.api.tools
  "Tool ingest functions in support of the ingest API."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]))

(defn- validate-and-prepare-tool-concept
  "Validate tool concept, set the concept format and returns the concept;
  throws error if the metadata is not valid against the UMM tool JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :tool))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn ingest-tool
  "Processes a request to create or update a tool."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        concept (api-core/body->concept!
                 :tool provider-id native-id body content-type headers)]
    (lt-validation/validate-launchpad-token request-context)
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission
      request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (validate-and-prepare-tool-concept concept)
          concept-with-user-id (api-core/set-user-id concept request-context headers)
          ;; Log the ingest attempt
          _ (info (format "Ingesting tool %s from client %s"
                          (api-core/concept->loggable-string concept-with-user-id)
                          (:client-id request-context)))
          save-tool-result (ingest/save-tool request-context concept-with-user-id)
          concept-to-log (api-core/concept-with-revision-id concept-with-user-id save-tool-result)]
      ;; Log the successful ingest, with the metadata size in bytes.
      (api-core/log-concept-with-metadata-size concept-to-log request-context)
      (api-core/generate-ingest-response headers save-tool-result))))

(defn delete-tool
  "Deletes the tool with the given provider id and native id."
  [provider-id native-id request]
  (api-core/delete-concept :tool provider-id native-id request))
