(ns cmr.ingest.api.services
  "Service ingest functions in support of the ingest API."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]))

(defn- verify-service-modification-permission
  "Verifies the current user has been granted permission to modify services in
  ECHO ACLs."
  [context permission-type]
  (when-not (seq (acl/get-permitting-acls context
                  :system-object
                  "INGEST_MANAGEMENT_ACL"
                  permission-type))
    (errors/throw-service-error
      :unauthorized
      (format "You do not have permission to %s a service." (name permission-type)))))

(defn- validate-service-content-type
  "Validates that content type sent with a service is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn create-service
  "Processes a create service request.

  IMPORTANT: Note that the permission require for creating a concept
             during ingest is :update and not :create. This is due to
             the fact that the ACL system for CMR has no 'create'
             permission in the system for any ingest; only 'update' is
             allowed for ingest operations. The mock ACL system (rightly)
             inherits this limitation from the real system."
  [context headers body]
  (verify-service-modification-permission context :update)
  (common-enabled/validate-write-enabled context "ingest")
  (validate-service-content-type headers)
  (api-core/generate-ingest-response
   headers
   (ingest/create-service context body)))

(defn update-service
  "Processes a request to update a service."
  [context headers body service-key]
  (verify-service-modification-permission context :update)
  (common-enabled/validate-write-enabled context "ingest")
  (validate-service-content-type headers)
  (api-core/generate-ingest-response
   headers
   (ingest/update-service context service-key body)))
