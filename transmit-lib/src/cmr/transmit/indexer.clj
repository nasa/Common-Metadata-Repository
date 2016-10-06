(ns cmr.transmit.indexer
  "Provides functions for accessing the indexer application"
  (:require [cmr.transmit.http-helper :as h]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as transmit-conn]))

;; Defines health check function
(h/defhealther get-indexer-health :indexer {:timeout-secs 2})

(h/defcacheclearer clear-cache :indexer)
