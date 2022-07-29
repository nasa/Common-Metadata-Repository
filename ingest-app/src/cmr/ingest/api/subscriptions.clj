(ns cmr.ingest.api.subscriptions
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.subscriptions-helper :as jobs]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.transmit.search :as search]
   [cmr.transmit.urs :as urs])
  (:import
   [java.util UUID]))

(def ^:private CMR_PROVIDER
  "CMR provider-id, used by collection subscription."
  "CMR")

(defn- subscriber-collection-permission-error
  [subscriber-id concept-id]
  (errors/throw-service-error
   :unauthorized
   (format "Collection with concept id [%s] does not exist or subscriber-id [%s] does not have permission to view the collection."
           concept-id
           subscriber-id)))

(defn- check-subscriber-collection-permission
  "Checks that the subscriber-id can read the collection supplied in the subscription metadata"
  [request-context parsed]
  (let [concept-id (:CollectionConceptId parsed)
        subscriber-id (:SubscriberId parsed)]
    (when (= "granule" (:Type parsed))
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
           concept-id))))))

(defn- search-concept-refs-with-sub-params
  "Performs a search using the provided query parameters. If the query is no good,
   we throw a service error."
  [context params concept-type]
  (when-let [errors (search/validate-search-params context params concept-type)]
    (errors/throw-service-error :bad-request
      (str "Subscription query validation failed with the following error(s): " errors))))

(defn- validate-query
  "Performs a search using subscription query parameters for purposes of validation"
  [context parsed]
  (let [collection-id (:CollectionConceptId parsed)
        query-string (:Query parsed)
        query-params (jobs/create-query-params query-string)
        subscription-type (or (keyword (:Type parsed)) :granule)
        search-params (merge
                       (when (= :granule subscription-type)
                         {:collection-concept-id collection-id})
                       {:token (config/echo-system-token)}
                       query-params)]
    (search-concept-refs-with-sub-params context search-params subscription-type)))

(defn- check-subscription-limit
  "Given the configuration for subscription limit, this valdiates that the user has no more than
  the limit before we allow more subscriptions to be ingested by that user."
  [request-context subscription parsed]
  (let [subscriber-id (:SubscriberId parsed)
        subscriptions (mdb/find-concepts
                       request-context
                       {:subscriber-id subscriber-id
                        :latest true}
                       :subscription)
        active-subscriptions (remove :deleted subscriptions)
        at-limit (>= (count active-subscriptions) (jobs/subscriptions-limit))
        native-id (:native-id subscription)
        exist? (some #(= native-id (:native-id %)) active-subscriptions)]
    (when (and at-limit (not exist?))
      (errors/throw-service-error
       :conflict
       (format "The subscriber-id [%s] has already reached the subscription limit."
               subscriber-id)))))

(defn- check-duplicate-subscription
  "The query used by a subscriber for a collection should be unique to prevent
   redundent emails from being sent to them. This function will check that a
   subscription is unique for the following conditions: native-id, collection-id,
   subscriber-id, normalized-query, subscription-type."
  [request-context subscription parsed]
  (let [native-id (:native-id subscription)
        provider-id (:provider-id subscription)
        normalized-query (:normalized-query subscription)
        collection-id (:CollectionConceptId parsed)
        subscriber-id (:SubscriberId parsed)
        ;; version 1.0 has no Type field and are granule subscriptions
        subscription-type (or (:Type parsed) "granule")
        ;; Find concepts with matching collection-concept-id, normalized-query, subscription-type
        ;; and subscriber-id
        duplicate-queries (mdb/find-concepts
                           request-context
                           (merge
                            (when (= "granule" subscription-type)
                              {:collection-concept-id collection-id})
                            {:normalized-query normalized-query
                             :subscriber-id subscriber-id
                             :subscription-type subscription-type
                             :exclude-metadata true
                             :latest true})
                           :subscription)
        ;;we only want to look at non-deleted subscriptions
        active-duplicate-queries (remove :deleted duplicate-queries)]
    ;;If there is at least one duplicate subscription,
    ;;We need to make sure it has the same native-id, or else reject the ingest
    (when (and (> (count active-duplicate-queries) 0)
               (every? #(not= native-id (:native-id %)) active-duplicate-queries))
      (if (= (keyword subscription-type) :granule)
        (errors/throw-service-error
         :conflict
         (format (str "The subscriber-id [%s] has already subscribed to the "
                      "collection with concept-id [%s] using the query [%s]. "
                      "Subscribers must use unique queries for each Collection.")
                 subscriber-id collection-id normalized-query))
        (errors/throw-service-error
         :conflict
         (format (str "The subscriber-id [%s] has already subscribed "
                      "using the query [%s]. "
                      "Subscribers must use unique queries for each collection subscription")
                 subscriber-id normalized-query))))))

(defn- perform-subscription-ingest
  "Perform the last set of validations and checks, then submit the save request."
  [request-context headers concept parsed]
  (check-duplicate-subscription request-context concept parsed)
  (check-subscription-limit request-context concept parsed)
  (check-subscriber-collection-permission request-context parsed)
  (let [concept-with-user-id (api-core/set-user-id concept
                                                   request-context
                                                   headers)
        ;; Log the ingest attempt before the save
        _ (info (format "Ingesting subscription %s from client %s"
                        (api-core/concept->loggable-string concept-with-user-id)
                        (:client-id request-context)))
        save-subscription-result (ingest/save-subscription request-context
                                                           concept-with-user-id)
        concept-to-log (api-core/concept-with-revision-id concept-with-user-id save-subscription-result)]
    ;; Log the successful ingest, with the metadata size in bytes.
    (api-core/log-concept-with-metadata-size concept-to-log request-context)
    (api-core/generate-ingest-response headers save-subscription-result)))

(defn- common-ingest-checks
  "Common checks needed before starting to process an ingest operation"
  [context]
  (common-enabled/validate-write-enabled context "ingest")
  (lt-validation/validate-launchpad-token context))

(defn- verify-coll-modification-permission
  "Verifies the current user has been granted permission to modify collection subscription.
  There is no good ACLs to handle this permission, so we use TAG_GROUP UPDATE permission for now."
  [context]
  (when-not (or (config/echo-system-token? context)
                (seq (acl/get-permitting-acls context :system-object "TAG_GROUP" :update)))
    (errors/throw-service-error
     :unauthorized
     "You do not have permission to perform that action.")))

(defn- check-provider-ingest-permission
  "Perform the provider level subscription ingest permission check"
  [context provider-id]
  (acl/verify-ingest-management-permission
    context :update :provider-object provider-id)
  (acl/verify-subscription-management-permission
    context :update :provider-object provider-id))

(defn- check-ingest-permission
  "Raise error if the user in the given context does not have the necessary permission to
   create/update/delete a subscription based on the new and old subscriber and existing ACLs."
  ([context provider-id new-subscriber]
   (check-ingest-permission context provider-id new-subscriber nil))
  ([context provider-id new-subscriber old-subscriber]
   (let [token-user (api-core/get-user-id-from-token context)]
     (if (and token-user
              (or (= token-user new-subscriber old-subscriber)
                  (and (= token-user new-subscriber)
                       (nil? old-subscriber))))
       (info (format "ACLs were bypassed because token user matches the subscriber [%s]."
                     new-subscriber))
       (do
         ;; log ACL checking message
         (if (and old-subscriber
                  (not= new-subscriber old-subscriber))
           (info (format "ACLs were checked because subscription subscriber is changed from [%s] to [%s]."
                         old-subscriber
                         new-subscriber))
           (info (format (str "ACLs were checked because token user [%s] is different from "
                              "the subscriber [%s] in the metadata.")
                         token-user
                         new-subscriber)))

         (if (= CMR_PROVIDER provider-id)
           (verify-coll-modification-permission context)
           (check-provider-ingest-permission context provider-id)))))))

(defn- validate-user-id
  "Raise error if the user provided does not exist"
  [context user-id]
  (when (string/blank? user-id)
    (errors/throw-service-error
     :bad-request
     "Subscription creation failed - No ID was provided. Please provide a SubscriberId or pass in a valid token."))
  (when-not (urs/user-exists? context user-id)
    (errors/throw-service-error
     :bad-request
     (format "Subscription creation failed - The user-id [%s] must correspond to a valid EDL account."
             user-id))))

(defn- validate-native-id-not-blank
  "Validate the given native-id is not blank. Raise error if it is."
  [native-id]
  (when (string/blank? native-id)
    (errors/throw-service-error
     :bad-request
     "Subscription native-id provided is blank.")))

(defn- generate-native-id
  "Generate a native-id for a subscription based on the name."
  [parsed]
  (-> parsed
      :Name
      csk/->snake_case
      (str "_" (UUID/randomUUID))))

(defn- native-id-collision?
  "Queries metadata db for a matching native-id."
  [context native-id]
  (let [query {:native-id native-id
               :exclude-metadata true
               :latest true}]
    (->> (mdb/find-concepts context query :subscription)
         (remove :deleted)
         seq)))

(defn- get-unique-native-id
  "Get a native-id that is unique by testing against the database."
  [context parsed]
  (let [native-id (generate-native-id parsed)]
    (if (native-id-collision? context native-id)
      (do
        (warn (format "Collision detected while generating native-id [%s], retrying." native-id))
        (get-unique-native-id context parsed))
      native-id)))

(defn- get-subscriber-id
  "Returns the subscription subscriber id.
   If in the metadata, return it; otherwise, get it from the token."
  [context parsed-metadata]
  (or (:SubscriberId parsed-metadata)
      (api-core/get-user-id-from-token context)))

(defn- body->subscription
  "Returns the subscription concept for the given request body, etc.
  This is the raw subscritpion that is ready for metadata validation,
  but still needs some sanitization to be saved to database."
  [native-id body content-type headers]
  (let [sub-concept (api-core/body->concept!
                     :subscription native-id body content-type headers)]
    (update-in sub-concept [:format] (partial ingest/fix-ingest-concept-format :subscription))))

(defn- perform-basic-validations
  "Perform some basic and schema related validations. Throws error if the metadata is invalid."
  [sub-concept]
  (v/validate-concept-request sub-concept)
  (let [schema-errs (v/validate-concept-metadata sub-concept false)]
    (when (some (set schema-errs) ["#: subject must not be valid against schema {\"required\":[\"CollectionConceptId\"]}"])
      (errors/throw-service-error
       :bad-request
       "Collection subscription cannot specify CollectionConceptId."))
    (when (some (set schema-errs) ["#: required key [CollectionConceptId] not found"])
      (errors/throw-service-error
       :bad-request
       "Granule subscription must specify CollectionConceptId."))
    (v/if-errors-throw :bad-request schema-errs)))

(defn- get-provider-id
  "Returns the provider-id for the given parsed subscription."
  [parsed]
  (let [{sub-type :Type coll-concept-id :CollectionConceptId} parsed]
    (if (= "collection" sub-type)
      CMR_PROVIDER
      (concepts/concept-id->provider-id coll-concept-id))))

(defn- validate-and-prepare-subscription-concept
  "Perform validations of the subscription concept.
  Returns a map of :concept with value of the new subscription concept with its metadata
  patched with SubscriberId if applicable; and :parsed with value of the parsed metadata."
  [context sub-concept]
  (perform-basic-validations sub-concept)
  (let [parsed (json/parse-string (:metadata sub-concept) true)
        provider-id (get-provider-id parsed)
        subscriber-id (get-subscriber-id context parsed)]
    (when-not (= CMR_PROVIDER provider-id)
      (api-core/verify-provider-exists context provider-id))
    (validate-user-id context subscriber-id)
    (validate-query context parsed)
    (let [parsed-metadata (assoc parsed :SubscriberId subscriber-id)]
      {:concept (assoc sub-concept
                       :metadata (json/generate-string parsed-metadata)
                       :normalized-query (sub-common/normalize-parameters (:Query parsed))
                       :subscription-type (or (:Type parsed) "granule")
                       :provider-id provider-id)
       :parsed parsed-metadata})))

(defn create-subscription
  "Processes a request to create a subscription. A native id will be generated."
  ([request]
   (create-subscription nil request))
  ([provider-id request]
   (let [{:keys [body content-type headers request-context]} request]
     (common-ingest-checks request-context)
     (let [tmp-subscription (body->subscription (str (UUID/randomUUID)) body content-type headers)
           {:keys [concept parsed]} (validate-and-prepare-subscription-concept
                                     request-context tmp-subscription)
           provider-id (:provider-id concept)
           subscriber-id (:SubscriberId parsed)
           native-id (get-unique-native-id request-context parsed)
           final-sub (assoc concept :native-id native-id)]
       (check-ingest-permission request-context provider-id subscriber-id)
       (perform-subscription-ingest request-context headers final-sub parsed)))))

(defn create-subscription-with-native-id
  "Processes a request to create a subscription using the native-id provided."
  ([native-id request]
   (create-subscription-with-native-id nil native-id request))
  ([provider-id native-id request]
   (validate-native-id-not-blank native-id)
   (let [{:keys [body content-type headers request-context]} request]
     (common-ingest-checks request-context)
     (let [tmp-subscription (body->subscription native-id body content-type headers)
           {:keys [concept parsed]} (validate-and-prepare-subscription-concept
                                     request-context tmp-subscription)
           provider-id (:provider-id concept)
           subscriber-id (:SubscriberId parsed)]
       (check-ingest-permission request-context provider-id subscriber-id)
       (when (native-id-collision? request-context native-id)
         (errors/throw-service-error
          :conflict
          (format "Subscription with native-id [%s] already exists." native-id)))
       (perform-subscription-ingest request-context headers concept parsed)))))

(defn create-or-update-subscription-with-native-id
  "Processes a request to create or update a subscription. This function
  does NOT fail on collisions. This is mapped to PUT methods to preserve
  existing functionality."
  ([native-id request]
   (create-or-update-subscription-with-native-id nil native-id request))
  ([provider-id native-id request]
   (validate-native-id-not-blank native-id)
   (let [{:keys [body content-type headers request-context]} request
         _ (common-ingest-checks request-context)
         tmp-subscription (body->subscription native-id body content-type headers)
         {:keys [concept parsed]} (validate-and-prepare-subscription-concept
                                   request-context tmp-subscription)
         provider-id (:provider-id concept)
         subscriber-id (:SubscriberId parsed)
         original-subscription (->> (mdb/find-concepts request-context
                                                       {:native-id native-id
                                                        :exclude-metadata false
                                                        :latest true}
                                                       :subscription)
                                    (remove :deleted)
                                    first)
         old-subscriber (when original-subscription
                          (get-in original-subscription [:extra-fields :subscriber-id]))]
     (check-ingest-permission request-context provider-id subscriber-id old-subscriber)
     (perform-subscription-ingest request-context headers concept parsed))))

(defn- prepare-delete-request
  "Validate the native-id for delete,
  return [provider-id subscriber-id] if the delete request can be processed."
  [context provider-id native-id]
  (let [sub-concepts (mdb/find-concepts context
                                        {:native-id native-id
                                         :exclude-metadata false
                                         :latest true}
                                        :subscription)]
    (if (seq sub-concepts)
      (let [sub-concept (first (remove :deleted sub-concepts))]
        (if sub-concept
          ;; there is a concept to delete, figure out the provider-id and subscriber-id
          (let [provider-id (if (= "collection" (get-in sub-concept [:extra-fields :subscription-type]))
                              CMR_PROVIDER
                              (if provider-id
                                provider-id
                                (get-provider-id (json/parse-string (:metadata sub-concept) true))))
                subscriber-id (get-in sub-concept [:extra-fields :subscriber-id])]
            [provider-id subscriber-id])
          (errors/throw-service-error
           :not-found
           (format "Subscription with native-id [%s] has already been deleted." native-id))))
      (errors/throw-service-error
       :not-found
       (format "Subscription with native-id [%s] does not exist." native-id)))))

(defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  ([native-id request]
   (delete-subscription nil native-id request))
  ([provider-id native-id request]
   (let [{:keys [body content-type headers request-context]} request
         _ (common-ingest-checks request-context)
         [provider-id subscriber-id] (prepare-delete-request request-context provider-id native-id)
         concept-attribs (-> {:provider-id provider-id
                              :native-id native-id
                              :concept-type :subscription}
                             (api-core/set-revision-id headers)
                             (api-core/set-user-id request-context headers))]
     (check-ingest-permission request-context provider-id subscriber-id)
     (info (format "Deleting subscription %s from client %s"
                   (pr-str concept-attribs) (:client-id request-context)))
     (api-core/generate-ingest-response headers
                                        (api-core/format-and-contextualize-warnings-existing-errors
                                          (ingest/delete-concept
                                           request-context
                                           concept-attribs))))))
