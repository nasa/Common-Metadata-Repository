(ns cmr.bootstrap.services.copy
  (:require [cmr.bootstrap.data.bulk-migration :as bm]
            [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]))
;; FIXME - I'd prefer to call this namespace data-migration instead of copy. It also needs a doc string.

;; FIXME - these operations should take the system and extract the channel they need.

(defn copy-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [channel provider-id]
  (info "Adding provider" provider-id "to provider channel")
  ;; FIXME this should be changed so that it will try to put a message on the channel. If it can't do
  ;; so immediately it should fail right away and return an error message to the user. My reasoning
  ;; for this is that I want attempts to queue another provider on a metadata db instance that's
  ;; busy to fail right away. We should discuss this though as I'm not sure.
  ;; Even if we want to keep the channel with a buffer we should still fail immediately if it's full
  ;; otherwise this will block here until a provider finishes.
  (go (>! channel provider-id)))

(defn copy-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [channel provider-id collection-id]
  (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
  (go (>! channel {:collection-id collection-id :provider-id provider-id})))