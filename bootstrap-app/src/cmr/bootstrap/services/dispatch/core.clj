(ns cmr.bootstrap.services.dispatch.core
  "Defines the Bootstrap dispatch protocol."
  (:require
   [cmr.bootstrap.services.dispatch.impl.async :as async]
   [cmr.bootstrap.services.dispatch.impl.message-queue :as message-queue]
   [cmr.bootstrap.services.dispatch.impl.sync :as sync])
  (:import
   (cmr.bootstrap.services.dispatch.impl.async CoreAsyncDispatcher)
   (cmr.bootstrap.services.dispatch.impl.message_queue MessageQueueDispatcher)
   (cmr.bootstrap.services.dispatch.impl.sync SynchronousDispatcher)))

(defprotocol Dispatch
  "Functions for handling the dispatch of bootstrap requests."

  (migrate-provider
   [this context provider-id]
   "Copy all the data for a provider (including collections and graunules) from catalog rest
   to the metadata db without blocking.")

  (migrate-collection
   [this context provider-id collection-id]
   "Copy all the data for a given collection (including graunules) from catalog rest
   to the metadata db without blocking.")

  (index-provider
   [this context provider-id start-index]
   "Bulk index all the collections and granules for a provider.")

  (index-data-later-than-date-time
   [this context date-time]
   "Bulk index all the concepts with a revision date later than the given date-time.")

  (index-collection
   [this context provider-id collection-id options]
   "Bulk index all the granules in a collection.")

  (index-variables
    [this context] [this context provider-id]
    "Bulk index all the variables in CMR. Optionally, pass a provider id, in
    which case only the variables for that provider will be indexed.")

  (index-system-concepts
   [this context start-index]
   "Bulk index all the tags, acls, and access-groups.")

  (index-concepts-by-id
   [this context provider-id concept-type concept-ids]
   "Bulk index the concepts given by the concept-ids")

  (delete-concepts-from-index-by-id
   [this context provider-id concept-type concept-ids]
   "Bulk delete the concepts given by the concept-ids from the indexes")

  (bootstrap-virtual-products
   [this context provider-id entry-title]
   "Initializes virtual products for the given provider and entry title."))

(extend CoreAsyncDispatcher
        Dispatch
        async/dispatch-behavior)

(extend MessageQueueDispatcher
        Dispatch
        message-queue/dispatch-behavior)

(extend SynchronousDispatcher
        Dispatch
        sync/dispatch-behavior)

(defn create-backend
  "Creates one of the specific implementations of Dispatchers."
  [backend-type]
  (case backend-type
    :async (async/create-core-async-dispatcher)
    :sync (sync/->SynchronousDispatcher)
    :message-queue (message-queue/->MessageQueueDispatcher)))

(defn subscribe-to-events
  "Subscribe to event messages on bootstrap queues. Pass through to message queue implementation."
  [context]
  (message-queue/subscribe-to-events context))
