(ns cmr.ingest.services.ingest-service.subscription
  (:require
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.ingest.services.nrt-subscription.subscriptions :as subscriptions]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn- add-extra-fields-for-subscription
  "Returns subscription concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [_context subscription concept]
  (assoc concept :extra-fields
                 {:subscription-name (:Name subscription)
                  :collection-concept-id (:CollectionConceptId subscription)
                  :subscriber-id (:SubscriberId subscription)
                  :subscription-type (or (:Type subscription) "granule")
                  :normalized-query (:normalized-query concept)
                  :endpoint (:EndPoint subscription)
                  :mode (:Mode subscription)
                  :method (:Method subscription)}))

(defn-timed save-subscription
  "Store a subscription concept in mdb and indexer."
  [context concept]
  (let [metadata (:metadata concept)
        subscription (spec/parse-metadata context :subscription (:format concept) metadata)
        concept (->> concept
                     (add-extra-fields-for-subscription context subscription)
                     (subscriptions/set-subscription-arn-if-applicable context :subscription))
        {:keys [concept-id revision-id]} (mdb2/save-concept
                                          context
                                          (assoc concept :provider-id (:provider-id concept)
                                                 :native-id (:native-id concept)))]
    (subscriptions/add-or-delete-ingest-subscription-in-cache context concept)
    {:concept-id concept-id
     :native-id (:native-id concept)
     :revision-id revision-id}))

(defn-timed delete-subscription
  "Delete a subscription from mdb and indexer. Throws a 404 error if the concept does not exist or
  the latest revision for the concept is already a tombstone."
  [context concept-attribs]
  (let [{:keys [concept-type provider-id native-id]} concept-attribs
        existing-concept (first (mdb/find-concepts context
                                                   {:provider-id provider-id
                                                    :native-id native-id
                                                    :latest true}
                                                   concept-type))
        concept-id (:concept-id existing-concept)]
    (when-not concept-id
      (errors/throw-service-error
       :not-found (cmsg/invalid-native-id-msg concept-type provider-id native-id)))
    (when (:deleted existing-concept)
      (errors/throw-service-error
       :not-found (format "Concept with native-id [%s] and concept-id [%s] is already deleted."
                          (util/html-escape native-id) (util/html-escape concept-id))))
    (let [concept (-> concept-attribs
                      (dissoc :provider-id :native-id)
                      (assoc :concept-id concept-id :deleted true))
          {:keys [revision-id]} (mdb/save-concept context concept)]
      (subscriptions/delete-ingest-subscription context existing-concept)
      {:concept-id concept-id, :revision-id revision-id})))

(defn refresh-subscription-cache
  "Go through all the subscriptions and create a map of collection concept ids and
  their mode values. Get the old keys from the cache and if the keys exist in the new structure
  then update the cache with the new values. Otherwise, delete the contents that no longer exists."
  [context]
  (subscriptions/refresh-subscription-cache context))
