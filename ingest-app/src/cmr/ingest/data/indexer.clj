(ns cmr.ingest.data.indexer
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to indexer app."
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.ingest.config :as config]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.connection :as transmit-conn]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.util :as util :refer [defn-timed]]
            [cmr.acl.core :as acl]
            [clojail.core :as timeout]))

(defn- get-headers
  "Gets the headers to use for communicating with the indexer."
  [context]
  (assoc (ch/context->http-headers context)
         transmit-config/token-header
         (transmit-config/echo-system-token)))

(defn- get-indexer-health-fn
  "Returns the health status of the indexer app"
  [context]
  (let [conn (transmit-config/context->app-connection context :indexer)
        request-url (str (transmit-conn/root-url conn) "/health")
        response (client/get request-url (merge
                                           (transmit-config/conn-params conn)
                                           {:accept :json
                                            :throw-exceptions false}))
        {:keys [status body]} response
        result (json/decode body true)]
    (if (= 200 status)
      {:ok? true :dependencies result}
      {:ok? false :problem result})))

(defn get-indexer-health
  "Returns the indexer health with timeout handling."
  [context]
  (let [timeout-ms (* 1000 (+ 2 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(get-indexer-health-fn context) timeout-ms)))


