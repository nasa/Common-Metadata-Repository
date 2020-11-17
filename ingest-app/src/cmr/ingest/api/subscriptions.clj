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

(defn- perform-subscription-ingest
  "This function assumes all checks have already taken place and that a
  subscription is ready to be saved"
  [concept request-context headers]
  (let [validated-concept (validate-and-prepare-subscription-concept concept)
        concept-with-user-id (api-core/set-user-id validated-concept
                                                   request-context
                                                   headers)
        ;; Log the ingest attempt before the save
        _ (info (format "Ingesting subscription %s from client %s"
                        (api-core/concept->loggable-string concept-with-user-id)
                        (:client-id request-context)))
        save-subscription-result (ingest/save-subscription request-context
                                                           concept-with-user-id)]
    ;; Log the successful ingest, with the metadata size in bytes.
    (api-core/log-concept-with-metadata-size concept-with-user-id request-context)
    (api-core/generate-ingest-response headers save-subscription-result)))

(defn ingest-subscription
  "Processes a request to create or update a subscription. Note, this will allow
  unlimited subscriptions to be created by users for themselfs. Revisit if this
  Becomes a problem"
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-enabled/validate-write-enabled request-context "ingest")
    (lt-validation/validate-launchpad-token request-context)
    (api-core/verify-provider-exists request-context provider-id)
    (let [concept (api-core/body->concept! :subscription provider-id native-id
                                           body
                                           content-type
                                           headers)
          subscription-user (:SubscriberId (json/decode (:metadata concept) true))
          token-user (api-core/get-user-id request-context headers)]
      (if (and token-user ;; don't allow a bypass of ACLs just because of nil values
               (= token-user subscription-user))
        (warn (format (str "ACLs were bypassed because the token account '%s' "
                      "matched the subscription user '%s' in the metadata.")
                      token-user
                      subscription-user))
        (do
          (info (format (str "ACLs were checked because the token user %s is "
                        "not the same as the subscription user %s in the "
                        "metadata.")
                        token-user
                        subscription-user))
          (acl/verify-ingest-management-permission request-context
                                                   :update
                                                   :provider-object
                                                   provider-id)
          (acl/verify-subscription-management-permission request-context
                                                         :update
                                                         :provider-object
                                                         provider-id)))
      (perform-subscription-ingest concept request-context headers))))

(defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  [provider-id native-id request]
  (acl/verify-subscription-management-permission
    (:request-context request) :update :provider-object provider-id)
  (api-core/delete-concept :subscription provider-id native-id request))
