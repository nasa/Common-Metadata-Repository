(ns cmr.ingest.data.ingest-events
  "Allows broadcast of ingest events via the message queue"
  (:require [cmr.ingest.config :as config]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.util :as util :refer [defn-timed]]
            [clojail.core :as timeout]))

(defn- try-to-publish
  "Attempts to publish messages to the given exchange.

  When the RabbitMQ server is down or unreachable, calls to publish will throw an exception. Rather
  than raise an error to the caller immediately, the publication will be retried indefinitely.
  By retrying, routine maintenance such as restarting the RabbitMQ server will not result in any
  ingest errors returned to the provider.

  Returns true if the message was successfully enqueued and false otherwise."
  [queue-broker exchange-name msg]
  (when-not (try
              (queue/publish-to-exchange queue-broker exchange-name msg)
              (catch Exception e
                (error e)
                false))
    (warn "Attempt to queue messaged failed. Retrying: " msg)
    (Thread/sleep 2000)
    (recur queue-broker exchange-name msg)))

(defn-timed publish-event
  "Put an ingest event on the message queue. Throws a service unavailable error if the message
  fails to be put on the queue.

  Requests to publish a message are wrapped in a timeout to handle error cases with the Rabbit MQ
  server. Otherwise failures to publish will be retried indefinitely."
  ([context msg]
   (publish-event context msg (config/publish-queue-timeout-ms)))
  ([context msg timeout-ms]
   (let [queue-broker (get-in context [:system :queue-broker])
         exchange-name (config/ingest-exchange-name)
         start-time (System/currentTimeMillis)]
     (try
       (timeout/thunk-timeout #(try-to-publish queue-broker exchange-name msg) timeout-ms)
       (catch java.util.concurrent.TimeoutException e
         (errors/throw-service-error
           :service-unavailable
           (str "Request timed out when attempting to publish message: " msg)
           e))))))

(defn collection-concept-update-event
  "Creates an event representing a collection concept being updated or created."
  [concept-id revision-id]
  {:action :concept-update
   :concept-id concept-id
   :revision-id revision-id})

(defn granule-concept-update-event
  "Creates an event representing a granule concept being updated or created."
  [coll-concept concept-id revision-id]
  {:action :concept-update
   ;; The entry title is used in the virtual product processing to avoid having to fetch the full
   ;; metadata to determine if this is a granule that requires processing.
   :entry-title (get-in coll-concept [:extra-fields :entry-title])
   :concept-id concept-id
   :revision-id revision-id})

(defn concept-delete-event
  "Creates an event representing a concept being deleted."
  [concept-id revision-id]
  {:action :concept-delete
   :concept-id concept-id
   :revision-id revision-id})

(defn provider-create-event
  "Creates an event representing a provider being created."
  [provider-id]
  {:action :provider-create
   :provider-id provider-id})

(defn provider-update-event
  "Creates an event representing a provider being updated."
  [provider-id]
  {:action :provider-update
   :provider-id provider-id})

(defn provider-delete-event
  "Creates an event representing a provider being deleted."
  [provider-id]
  {:action :provider-delete
   :provider-id provider-id})

