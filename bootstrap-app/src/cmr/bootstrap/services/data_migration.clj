(ns cmr.bootstrap.services.data-migration
  "Provides methods to insert migration requets on the approriate channels."
  (:require [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.bootstrap.data.bulk-migration :as bm]))

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [context provider-id synchronous]
  (if synchronous
    (bm/copy-provider (:system context) provider-id)
    (let [channel (get-in context [:system :provider-db-channel])]
      (info "Adding provider" provider-id "to provider channel")
      (go (>! channel provider-id)))))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [context provider-id collection-id synchronous]
  (if synchronous
    (bm/copy-single-collection (:system context) provider-id collection-id)
    (let [channel (get-in context [:system :collection-db-channel])]
      (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
      (go (>! channel {:collection-id collection-id :provider-id provider-id})))))