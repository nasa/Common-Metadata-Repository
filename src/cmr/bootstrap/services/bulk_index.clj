(ns cmr.bootstrap.services.bulk-index
  "Provides methods to insert bulk index requets on the approriate channels."
  (:require [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [system provider-id]
  (let [channel (:provider-index-channel system)]
    (info "Adding provider" provider-id "to provider index channel")
    (go (>! channel provider-id))))