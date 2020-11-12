(ns cmr.ingest.api.subscriptions
  "Subscription ingest functions in support of the ingest API."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]))

(defn- validate-and-prepare-subscription-concept
  "Validate subscription concept, set the concept format and returns the concept;
  throws error if the metadata is not a valid against the UMM subscription JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :subscription))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn subscription-user-matches-token?
  "Check to see if the user for the supplied token matches the user in the subscription request"
  [concept request-context headers]
  (let [token-user (api-core/get-user-id request-context headers)
        subscriber (get (json/decode (:metadata concept)) "SubscriberId")]
        (= subscriber token-user)))

(defn ingest-subscription
  "Processes a request to create or update a subscription."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        concept (api-core/body->concept!
                 :subscription provider-id native-id body content-type headers)
        _ (lt-validation/validate-launchpad-token request-context)
        _ (api-core/verify-provider-exists request-context provider-id)
        subscription-user (get (json/decode (:metadata concept)) "SubscriberId")
        token-user (api-core/get-user-id request-context headers)
        bypass-acls (subscription-user-matches-token? concept request-context headers)
        _ (when-not bypass-acls
                    (info "ACLs were checked because the token user"
                          token-user
                          "is not the same as the subscription user"
                          subscription-user)
                    (acl/verify-ingest-management-permission
                      request-context :update :provider-object provider-id)
                    (acl/verify-subscription-management-permission
                      request-context :update :provider-object provider-id))
        _ (common-enabled/validate-write-enabled request-context "ingest")
        concept (validate-and-prepare-subscription-concept concept)
        concept-with-user-id (api-core/set-user-id concept request-context headers)
        ;; Log the ingest attempt
        _ (info (format "Ingesting subscription %s from client %s"
                        (api-core/concept->loggable-string concept-with-user-id)
                        (:client-id request-context)))
        save-subscription-result (ingest/save-subscription request-context concept-with-user-id)]
    ;; Log the successful ingest, with the metadata size in bytes.

    ; there is a worry that subscriptions could be created neferiusly, log the
    ; action. When no longer needed (log) also remove the variables from the let.
    (when bypass-acls
      (warn "ACLs were bypassed because the requesting account" token-user
        "matched the subscription user" subscription-user ""))

    (api-core/log-concept-with-metadata-size concept-with-user-id request-context)
    (api-core/generate-ingest-response headers save-subscription-result)))

(defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  [provider-id native-id request]
  (acl/verify-subscription-management-permission
    (:request-context request) :update :provider-object provider-id)
  (api-core/delete-concept :subscription provider-id native-id request))
