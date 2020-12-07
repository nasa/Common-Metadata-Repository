(ns cmr.ingest.api.subscriptions
  "Subscription ingest functions in support of the ingest API."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.metadata-db :as mdb]))

(defn- validate-and-prepare-subscription-concept
  "Validate subscription concept, set the concept format and returns the concept;
  throws error if the metadata is not a valid against the UMM subscription JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :subscription))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn- subscriber-collection-permission-error
  [subscriber-id concept-id]
  (errors/throw-service-error
   :unauthorized
   (format "Collection with concept id [%s] does not exist or subscriber-id [%s] does not have permission to view the collection."
           concept-id
           subscriber-id)))

(defn- check-subscriber-collection-permission
  "Checks that the subscriber-id can read the collection supplied in the subscription metadata"
  [request-context concept]
  (let [metadata (-> (:metadata concept)
                     (json/decode true))
        concept-id (:CollectionConceptId metadata)
        subscriber-id (:SubscriberId metadata)]
      (try
        (let [permissions (-> (access-control/get-permissions request-context
                                                              {:concept_id concept-id
                                                               :user_id subscriber-id})
                              json/decode
                              (get concept-id))]
          (when-not (some #{"read"} permissions)
            (subscriber-collection-permission-error
             subscriber-id
             concept-id)))
        (catch Exception e
          (subscriber-collection-permission-error
           subscriber-id
           concept-id)))))

(defn- perform-subscription-ingest
  "This function assumes all checks have already taken place and that a
  subscription is ready to be saved"
  [request-context concept headers]
  (let [validated-concept (validate-and-prepare-subscription-concept concept)
        _ (check-subscriber-collection-permission request-context concept)
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

(defn- common-ingest-checks
  "Common checks needed before starting to process an ingest operation"
  [request-context provider-id]
  (common-enabled/validate-write-enabled request-context "ingest")
  (lt-validation/validate-launchpad-token request-context)
  (api-core/verify-provider-exists request-context provider-id))

(defn- check-subscription-ingest-permission
  "All the checks needed before starting to process an ingest of subscriptions"
  [request-context concept headers provider-id]
  (let [old-concept-subscriber (-> (mdb/find-concepts request-context
                                                      {:provider-id provider-id
                                                       :native-id (:native-id concept)
                                                       :exclude-metadata false
                                                       :latest true}
                                                      :subscription)
                                   first
                                   :extra-fields
                                   :subscriber-id)
        subscription-user (if old-concept-subscriber
                            old-concept-subscriber
                            (:SubscriberId (json/decode (:metadata concept) true)))
        token-user (api-core/get-user-id request-context headers)]
    (if (and token-user
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
        (acl/verify-ingest-management-permission
          request-context :update :provider-object provider-id)
        (acl/verify-subscription-management-permission
          request-context :update :provider-object provider-id)))))

(defn ingest-subscription
  "Processes a request to create or update a subscription. Note, this will allow
  unlimited subscriptions to be created by users for themselves. Revisit if this
  Becomes a problem"
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (let [concept (api-core/body->concept!
                    :subscription provider-id native-id body content-type headers)]
      (check-subscription-ingest-permission request-context concept headers provider-id)
      (perform-subscription-ingest request-context concept headers))))

(defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        _ (common-ingest-checks request-context provider-id)
        concept-type :subscription
        concept (first (mdb/find-concepts request-context
                                          {:provider-id provider-id
                                           :native-id native-id
                                           :exclude-metadata false
                                           :latest true}
                                          concept-type))]
    (check-subscription-ingest-permission request-context concept headers provider-id)
    (let [concept-attribs (-> {:provider-id provider-id
                               :native-id native-id
                               :concept-type concept-type}
                              (api-core/set-revision-id headers)
                              (api-core/set-user-id request-context headers))]
      (info (format "Deleting %s %s from client %s"
                    (name concept-type) (pr-str concept-attribs) (:client-id request-context)))
      (api-core/generate-ingest-response headers
                                         (api-core/format-and-contextualize-warnings
                                          (ingest/delete-concept
                                           request-context
                                           concept-attribs))))))
