(ns cmr.bootstrap.services.bulk-index
  "Provides methods to insert bulk index requets on the approriate channels."
  (:require [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.bootstrap.data.bulk-index :as bulk]
            [cmr.common.services.errors :as errors]))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [system provider-id synchronous]
  (if synchronous
    (bulk/index-provider system provider-id)
    (let [channel (:provider-index-channel system)]
      (info "Adding provider" provider-id "to provider index channel")
      (go (>! channel provider-id)))))

(defn index-collection
  "Bulk index all the granules in a collection"
  [system provider-id collection-id synchronous]
  (if synchronous
    (bulk/index-granules-for-collection system provider-id collection-id)
    (let [channel (:collection-index-channel system)]
      (info "Adding collection" collection-id "to collection index channel")
      (go (>! channel [provider-id collection-id])))))