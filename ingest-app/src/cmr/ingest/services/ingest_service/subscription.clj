(ns cmr.ingest.services.ingest-service.subscription
  (:require
   [cmr.common.util :refer [defn-timed]]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn- add-extra-fields-for-subscription
  "Returns subscription concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept subscription]
  (assoc concept :extra-fields
                 {:subscription-name (:Name subscription)
                  :collection-concept-id (:CollectionConceptId subscription)
                  :subscriber-id (:SubscriberId subscription)
                  :subscription-type (or (:Type subscription) "granule")
                  :normalized-query (:normalized-query concept)}))

(defn-timed save-subscription
  "Store a subscription concept in mdb and indexer."
  [context concept]
  (let [metadata (:metadata concept)
        subscription (spec/parse-metadata context :subscription (:format concept) metadata)
        concept (add-extra-fields-for-subscription context concept subscription)
        {:keys [concept-id revision-id]} (mdb2/save-concept
                                          context
                                          (assoc concept :provider-id (:provider-id concept)
                                                 :native-id (:native-id concept)))]
    {:concept-id concept-id
     :native-id (:native-id concept)
     :revision-id revision-id}))
