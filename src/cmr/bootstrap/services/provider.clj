(ns cmr.bootstrap.services.provider
  (:require [cmr.bootstrap.data.bulk-migration :as bm]
            [clojure.core.async :as async :refer [go >!]]))

(defn copy-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [channel provider-id]
  (println "Adding provider" provider-id " to channel" channel)
  (go (>! channel provider-id)))


(comment

(copy-provider (:provider-channel user/system) 100)
  )