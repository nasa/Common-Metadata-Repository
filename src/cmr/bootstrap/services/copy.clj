(ns cmr.bootstrap.services.copy
  (:require [cmr.bootstrap.data.bulk-migration :as bm]
            [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]))

(defn copy-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [channel provider-id]
  (info "Adding provider" provider-id "to provider channel")
  (go (>! channel provider-id)))

(defn copy-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [channel provider-id collection-id]
  (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
  (go (>! channel {:collection-id collection-id :provider-id provider-id})))