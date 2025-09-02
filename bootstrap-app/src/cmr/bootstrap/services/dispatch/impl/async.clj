(ns cmr.bootstrap.services.dispatch.impl.async
  "Provides methods to insert migration requets on the appropriate channels."
  (:require
    [clojure.core.async :as async :refer [>!]]
    [cmr.common.log :refer [info]]
    [cmr.common.services.errors :as errors]))

(defn- not-implemented
  "Throws an exception indicating that the specified function is not implemented for
  the async dispatcher."
  [action & _]
  (errors/internal-error!
   (format "Async Dispatcher does not support %s action." (name action))))

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [this context provider-id]
  (let [channel (:provider-db-channel this)]
    (info "Adding provider" provider-id "to provider channel")
    (async/go (>! channel provider-id))))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [this context provider-id collection-id]
  (let [channel (:collection-db-channel this)]
    (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
    (async/go (>! channel {:collection-id collection-id :provider-id provider-id}))))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [this context provider-id start-index]
  (let [channel (:provider-index-channel this)]
    (info "Adding provider" provider-id "to provider index channel")
    (async/go (>! channel {:provider-id provider-id
                           :start-index start-index}))))

(defn index-collection
  "Bulk index all the granules in a collection"
  [this context provider-id collection-id options]
  (let [channel (:collection-index-channel this)]
    (info "Adding collection" collection-id "to collection index channel")
    (async/go (>! channel (merge options
                                 {:provider-id provider-id
                                  :collection-id collection-id})))))

(defn index-system-concepts
  "Bulk index all the tags, acls, and access-groups."
  [this context start-index]
  (let [channel (:system-concept-channel this)]
    (info "Adding bulk index request to system concepts channel.")
    (async/go (>! channel {:start-index start-index}))))

(defn index-concepts-by-id
  "Bulk index the concepts given by the concept-ids"
  [this context provider-id concept-type concept-ids]
  (let [channel (:concept-id-channel this)]
    (info "Adding bulk index request to concept-id channel.")
    (async/go (>! channel {:provider-id provider-id
                           :concept-type concept-type
                           :request :index
                           :concept-ids concept-ids}))))

(defn delete-concepts-from-index-by-id
  "Bulk delete the concepts given by the concept-ids from the indexes"
  [this context provider-id concept-type concept-ids]
  (let [channel (:concept-id-channel this)]
    (info "Adding bulk delete reqeust to concept-id channel.")
    (async/go (>! channel {:provider-id provider-id
                           :concept-type concept-type
                           :request :delete
                           :concept-ids concept-ids}))))

(defn bootstrap-virtual-products
  "Initializes virtual products."
  [this context provider-id entry-title]
  (let [channel (:virtual-product-channel this)]
    (info "Adding message to virtual products channel.")
    (async/go (>! channel {:provider-id provider-id
                           :entry-title entry-title}))))

(defrecord CoreAsyncDispatcher
  [;; Channel for requesting full provider migration
   provider-db-channel
   ;; Channel for requesting single collection/granules migration.
   ;; Takes maps, e.g., {:collection-id collection-id :provider-id provider-id}
   collection-db-channel
   ;; Channel for requesting full provider indexing - collections/granules
   provider-index-channel
   ;; Channel for processing collections to index.
   collection-index-channel
   ;; Channel for processing bulk index requests for system concepts (tags, acls, access-groups)
   system-concept-channel
   ;; channel for processing bulk index requests by concept-id
   concept-id-channel
   ;; channel for bootstrapping virtual products
   virtual-product-channel])

(def dispatch-behavior
  "Map of protocol definitions to the implementations of that protocol for the
  dispatcher."
  {:migrate-provider migrate-provider
   :migrate-collection migrate-collection
   :index-provider index-provider
   :index-variables (partial not-implemented :index-variables)
   :index-services (partial not-implemented :index-services)
   :index-data-later-than-date-time (partial not-implemented :index-data-later-than-date-time)
   :index-collection index-collection
   :index-system-concepts index-system-concepts
   :index-concepts-by-id index-concepts-by-id
   :index-generics (partial not-implemented :index-generics)
   :delete-concepts-from-index-by-id delete-concepts-from-index-by-id
   :bootstrap-virtual-products bootstrap-virtual-products
   :fingerprint-variables (partial not-implemented :fingerprint-variables)})

(defn- create-default-channels
  "Creates channels needed for all bootstrapping work and returns as a map of channel name to
  channel."
  []
  {:provider-db-channel (async/chan 10)
   :collection-db-channel (async/chan 100)
   :provider-index-channel (async/chan 10)
   :collection-index-channel (async/chan 100)
   :system-concept-channel (async/chan 10)
   :concept-id-channel (async/chan 10)
   :virtual-product-channel (async/chan)})

(defn create-core-async-dispatcher
  "Creates a new core async dispatcher."
  []
  (let [channels (create-default-channels)]
    (->CoreAsyncDispatcher (:provider-db-channel channels)
                           (:collection-db-channel channels)
                           (:provider-index-channel channels)
                           (:collection-index-channel channels)
                           (:system-concept-channel channels)
                           (:concept-id-channel channels)
                           (:virtual-product-channel channels))))
