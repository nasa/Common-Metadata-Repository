(ns cmr.bootstrap.services.provider
  (:require [cmr.bootstrap.data.bulk-migration :as bm]
            [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]))

(defn copy-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [channel provider-id]
  (info "Adding provider" provider-id "to provider channel")
  (go (>! channel provider-id)))