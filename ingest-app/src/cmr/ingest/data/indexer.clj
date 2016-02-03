(ns cmr.ingest.data.indexer
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to indexer app."
  (:require [cmr.transmit.http-helper :as h]
            [cmr.common.api.context :as ch]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.connection :as transmit-conn]))

(defn- get-headers
  "Gets the headers to use for communicating with the indexer."
  [context]
  (assoc (ch/context->http-headers context)
         transmit-config/token-header
         (transmit-config/echo-system-token)))

;; Defines health check function
(h/defhealther get-indexer-health :indexer 2)