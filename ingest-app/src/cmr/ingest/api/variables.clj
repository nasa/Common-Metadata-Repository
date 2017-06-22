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

(defn- verify-variable-modification-permission
  "Verifies the current user has been granted permission to modify variables in
  ECHO ACLs."
  [context permission-type]
  (when-not (seq (acl/get-permitting-acls context
                  :system-object
                  "INGEST_MANAGEMENT_ACL"
                  permission-type))
    (errors/throw-service-error
      :unauthorized
      (format "You do not have permission to %s a variable." (name permission-type)))))

(defn- validate-variable-content-type
  "Validates that content type sent with a variable is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- validate-variable-metadata
  "Validate variable metadata, throws error if the metadata is not a valid against the
   UMM variable JSON schema."
  [content-type headers metadata]
  (let [concept (api-core/metadata->concept :variable metadata content-type headers)
        concept (update-in concept [:format] ingest/fix-ingest-concept-format)]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)))

(defn create-variable
  "Processes a create variable request.

  IMPORTANT: Note that the permission require for creating a concept
             during ingest is :update and not :create. This is due to
             the fact that the ACL system for CMR has no 'create'
             permission in the system for any ingest; only 'update' is
             allowed for ingest operations. The mock ACL system (rightly)
             inherits this limitation from the real system."
  [request]
  (let [{:keys [body content-type headers request-context]} request
        metadata (string/trim (slurp body))]
    (verify-variable-modification-permission request-context :update)
    (common-enabled/validate-write-enabled request-context "ingest")
    (validate-variable-metadata content-type headers metadata)
    (api-core/generate-ingest-response
     headers
     (ingest/create-variable request-context metadata))))

(defn update-variable
  "Processes a request to update a variable."
  [variable-key request]
  (let [{:keys [body content-type headers request-context]} request
        metadata (string/trim (slurp body))]
    (verify-variable-modification-permission request-context :update)
    (common-enabled/validate-write-enabled request-context "ingest")
    (validate-variable-metadata content-type headers metadata)
    (api-core/generate-ingest-response
     headers
     (ingest/update-variable request-context variable-key metadata))))

(defn delete-variable
  "Deletes the variable with the given variable-key."
  [context headers variable-key]
  (verify-variable-modification-permission context :update)
  (common-enabled/validate-write-enabled context "ingest")
  (api-core/generate-ingest-response
   headers
   (ingest/delete-variable context variable-key)))
