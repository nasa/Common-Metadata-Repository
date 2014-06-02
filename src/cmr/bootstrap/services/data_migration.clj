(ns cmr.bootstrap.services.data-migration
  "Provides methods to insert migration requets on the approriate channels."
  (:require [cmr.bootstrap.data.bulk-migration :as bm]
            [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]))

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [system provider-id]
  (let [channel (:provider-channel system)]
    (info "Adding provider" provider-id "to provider channel")
    (go (>! channel provider-id))))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [system provider-id collection-id]
  (let [channel (:collection-channel system)]
    (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
    (go (>! channel {:collection-id collection-id :provider-id provider-id}))))