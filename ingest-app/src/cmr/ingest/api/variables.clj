(ns cmr.ingest.api.variables
  "Variable ingest functions in support of the ingest API."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :refer [body->concept ingest-status-code]]
   [cmr.ingest.services.ingest-service :as ingest]))

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

(defn- api-response
  "Creates a successful variable response with the given data response"
  ([data]
   (api-response 200 data))
  ([status-code data]
   {:status status-code
    :body (json/generate-string data)
    :headers {"Content-Type" mt/json}}))

(defn create-variable
  "Processes a create variable request.

  IMPORTANT: Note that the permission require for creating a concept
             during ingest is :update and not :create. This is due to
             the fact that the ACL system for CMR has no 'create'
             permission in the system for any ingest; only 'update' is
             allowed for ingest operations. The mock ACL system (rightly)
             inherits this limitation from the real system."
  [context headers body]
  (verify-variable-modification-permission context :update)
  (common-enabled/validate-write-enabled context "ingest")
  (validate-variable-content-type headers)
  (let [result (ingest/create-variable context body)
        status-code (ingest-status-code result)]
    (api-response status-code result)))

(defn update-variable
  "Processes a request to update a variable."
  [context headers body variable-key]
  (verify-variable-modification-permission context :update)
  (common-enabled/validate-write-enabled context "ingest")
  (validate-variable-content-type headers)
  (api-response (ingest/update-variable context variable-key body)))

