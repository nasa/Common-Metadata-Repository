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
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
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

(defn- is-subscription-revision?
  "True if the subscription is a revision, otherwise false."
  [subscription active-subscriptions]
  (let [pred #(and
                (= (:native-id %) (:native-id subscription))
                (= (:provider-id %) (:provider-id subscription)))]
    (boolean (seq (filter pred active-subscriptions)))))

(defn- check-subscription-limit
  "Given the  configuration for subscription limit, this valdiates that the
  user has no more than the limit before we allow more subscriptions to be
  ingested by that user."
  [request-context subscription parsed]
  (let [subscriber-id (:SubscriberId parsed)
        subscriptions (mdb/find-concepts
                       request-context
                       {:subscriber-id subscriber-id
                        :latest true}
                       :subscription)
        active-subscriptions (remove :deleted subscriptions)
        at-limit (>= (count active-subscriptions) (jobs/subscriptions-limit))
        is-not-revision #(not (is-subscription-revision? subscription active-subscriptions))]
    (when (and at-limit (is-not-revision))
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
  [request-context provider-id]
  (common-enabled/validate-write-enabled request-context "ingest")
  (lt-validation/validate-launchpad-token request-context)
  (api-core/verify-provider-exists request-context provider-id))

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

         (acl/verify-ingest-management-permission
           context :update :provider-object provider-id)
         (acl/verify-subscription-management-permission
           context :update :provider-object provider-id))))))

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

(defn- generate-native-id
  "Generate a native-id for a subscription based on the name."
  [parsed]
  (-> parsed
      :Name
      csk/->snake_case
      (str "_" (UUID/randomUUID))))

(defn native-id-collision?
  "Queries metadata db for a matching provider-id and native-id pair."
  [context provider-id native-id]
  (let [query {:provider-id provider-id
               :native-id native-id
               :exclude-metadata true
               :latest true}]
    (-> context
        (mdb/find-concepts query :subscription)
        seq)))

(defn- get-unique-native-id
  "Get a native-id that is unique by testing against the database."
  [context provider-id parsed]
  (let [native-id (generate-native-id parsed)]
    (if (native-id-collision? context provider-id native-id)
      (do
        (warn (format "Collision detected while generating native-id [%s] for provider [%s], retrying."
                      native-id provider-id))
        (get-unique-native-id context provider-id parsed))
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
  [provider-id native-id body content-type headers]
  (let [sub-concept (api-core/body->concept!
                     :subscription provider-id native-id body content-type headers)]
    (update-in sub-concept [:format] (partial ingest/fix-ingest-concept-format :subscription))))

(defn- validate-business-rules
  "Raise error if the subscription concept does not pass business rule validations."
  [parsed]
  (let [{sub-type :Type coll-concept-id :CollectionConceptId} parsed]
    ;; even though the JSON schema validation will catch these errors,
    ;; we do the validation here to get a better error message.
    (when (and (= "granule" sub-type)
               (string/blank? coll-concept-id))
      (errors/throw-service-error
       :bad-request
       "Granule subscription must specify CollectionConceptId."))
    (when (and (= "collection" sub-type)
               (some? coll-concept-id))
      (errors/throw-service-error
       :bad-request
       (format "Collection subscription cannot specify CollectionConceptId, but was %s."
               coll-concept-id)))))

(defn- perform-basic-validations
  "Perform some basic and schema related validations. Throws error if the metadata is invalid."
  [parsed sub-concept]
  (validate-business-rules parsed)
  (v/validate-concept-request sub-concept)
  (v/validate-concept-metadata sub-concept))

(defn- validate-and-prepare-subscription-concept
  "Perform the validations of the subscription request fields.
  Returns a map with :concept value of the new subscription concept with its metadata added with
  SubscriberId if applicable; and :parsed value of the parsed metadata."
  [context provider-id sub-concept]
  (let [parsed (json/parse-string (:metadata sub-concept) true)
        _ (perform-basic-validations parsed sub-concept)
        subscriber-id (get-subscriber-id context parsed)]
    (validate-user-id context subscriber-id)
    (validate-query context parsed)
    (let [parsed-metadata (assoc parsed :SubscriberId subscriber-id)]
      {:concept (assoc sub-concept
                       :metadata (json/generate-string parsed-metadata)
                       :normalized-query (sub-common/normalize-parameters (:Query parsed))
                       :subscription-type (or (:Type parsed) "granule"))
       :parsed parsed-metadata})))

(defn create-subscription
  "Processes a request to create a subscription. A native id will be generated."
  [provider-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (let [tmp-subscription (body->subscription
                            provider-id (str (UUID/randomUUID)) body content-type headers)
          {:keys [concept parsed]} (validate-and-prepare-subscription-concept
                                    request-context provider-id tmp-subscription)
          subscriber-id (:SubscriberId parsed)
          native-id (get-unique-native-id request-context provider-id parsed)
          final-sub (assoc concept :native-id native-id)]
      (check-ingest-permission request-context provider-id subscriber-id)
      (perform-subscription-ingest request-context headers final-sub parsed))))

(defn create-subscription-with-native-id
  "Processes a request to create a subscription using the native-id provided."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (let [tmp-subscription (body->subscription provider-id native-id body content-type headers)
          {:keys [concept parsed]} (validate-and-prepare-subscription-concept
                                    request-context provider-id tmp-subscription)
          subscriber-id (:SubscriberId parsed)]
      (check-ingest-permission request-context provider-id subscriber-id)
      (when (native-id-collision? request-context provider-id native-id)
        (errors/throw-service-error
         :conflict
         (format "Subscription with provider-id [%s] and native-id [%s] already exists."
                 provider-id
                 native-id)))
      (perform-subscription-ingest request-context headers concept parsed))))

(defn create-or-update-subscription-with-native-id
  "Processes a request to create or update a subscription. This function
  does NOT fail on collisions. This is mapped to PUT methods to preserve
  existing functionality."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        _ (common-ingest-checks request-context provider-id)
        tmp-subscription (body->subscription provider-id native-id body content-type headers)
        {:keys [concept parsed]} (validate-and-prepare-subscription-concept
                                  request-context provider-id tmp-subscription)
        subscriber-id (:SubscriberId parsed)
        old-subscriber (when-let [original-subscription
                                  (first (mdb/find-concepts
                                          request-context
                                          {:provider-id provider-id
                                           :native-id native-id
                                           :exclude-metadata false
                                           :latest true}
                                          :subscription))]
                         (get-in original-subscription [:extra-fields :subscriber-id]))]
    (check-ingest-permission request-context provider-id subscriber-id old-subscriber)
    (perform-subscription-ingest request-context headers concept parsed)))

(defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        _ (common-ingest-checks request-context provider-id)
        subscriber-id (when-let [subscription (first (mdb/find-concepts
                                                      request-context
                                                      {:provider-id provider-id
                                                       :native-id native-id
                                                       :exclude-metadata false
                                                       :latest true}
                                                      :subscription))]
                        (get-in subscription [:extra-fields :subscriber-id]))
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
                                         concept-attribs)))))
