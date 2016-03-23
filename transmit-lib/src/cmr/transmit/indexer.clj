(ns cmr.transmit.indexer
  "Provides functions for accessing the indexer application"
  (:require [cmr.transmit.http-helper :as h]
            [cmr.common.api.context :as ch]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as transmit-conn]))

(defn clear-cache-url
  [conn]
  (format "%s/caches/clear-cache"))

;; Defines health check function
(h/defhealther get-indexer-health :indexer 2)

(defn clear-cache
  "Clears the indexer cache. Will only clear the instance it talks to (unless one of the caches uses
   a consistent cache.)"
  [context]
  (h/request context :indexer
             {:url-fn clear-cache-url
              :method :post
              :http-options {:headers {config/token-header (config/echo-system-token)}}}))
