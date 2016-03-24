(ns cmr.bootstrap.services.bootstrap-service
  "Provides methods to insert migration requets on the approriate channels."
  (:require [clojure.core.async :as async :refer [go >!]]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as concepts]
            [cmr.bootstrap.config :as cfg]
            [cmr.bootstrap.data.bulk-index :as bulk]
            [cmr.bootstrap.data.bulk-migration :as bm]
            [cmr.bootstrap.data.virtual-products :as vp]
            [cmr.transmit.index-set :as index-set]
            [cmr.transmit.indexer :as indexer]
            [cmr.indexer.data.index-set :as indexer-index-set]
            [cmr.bootstrap.data.rebalance-util :as rebalance-util]))

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [context provider-id synchronous]
  (if synchronous
    (bm/copy-provider (:system context) provider-id)
    (let [channel (get-in context [:system :provider-db-channel])]
      (info "Adding provider" provider-id "to provider channel")
      (go (>! channel provider-id)))))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [context provider-id collection-id synchronous]
  (if synchronous
    (bm/copy-single-collection (:system context) provider-id collection-id)
    (let [channel (get-in context [:system :collection-db-channel])]
      (info "Adding collection"  collection-id "for provider" provider-id "to collection channel")
      (go (>! channel {:collection-id collection-id :provider-id provider-id})))))

(defn- get-provider
  "Returns the metadata db provider that matches the given provider id. Throws exception if
  no matching provider is found."
  [context provider-id]
  (if-let [provider (bulk/get-provider-by-id context provider-id)]
    provider
    (errors/throw-service-errors :bad-request
                              [(format "Provider: [%s] does not exist in the system" provider-id)])))

(defn validate-collection
  "Validates to be bulk_indexed collection exists in cmr else an exception is thrown."
  [context provider-id collection-id]
  (let [provider (get-provider context provider-id)]
    (when-not (bulk/get-collection context provider collection-id)
      (errors/throw-service-errors :bad-request
                                [(format "Collection [%s] does not exist." collection-id)]))))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [context provider-id synchronous start-index]
  (get-provider context provider-id)
  (if synchronous
    (bulk/index-provider (:system context) provider-id start-index)
    (let [channel (get-in context [:system :provider-index-channel])]
      (info "Adding provider" provider-id "to provider index channel")
      (go (>! channel {:provider-id provider-id
                       :start-index start-index})))))

(defn index-collection
  "Bulk index all the granules in a collection"
  ([context provider-id collection-id synchronous]
   (index-collection context provider-id collection-id synchronous nil))
  ([context provider-id collection-id synchronous target-index-key]
   (validate-collection context provider-id collection-id)
   (if synchronous
     (bulk/index-granules-for-collection (:system context) provider-id collection-id target-index-key)
     (let [channel (get-in context [:system :collection-index-channel])]
       (info "Adding collection" collection-id "to collection index channel")
       (go (>! channel {:provider-id provider-id
                        :collection-id collection-id
                        :target-index-key target-index-key}))))))

(defn bootstrap-virtual-products
  "Initializes virtual products."
  [context synchronous provider-id entry-title]
  (if synchronous
    (vp/bootstrap-virtual-products (:system context) provider-id entry-title)
    (go
      (info "Adding message to virtual products channel.")
      (-> context :system (get vp/channel-name) (>! {:provider-id provider-id
                                                     :entry-title entry-title})))))
(defn start-rebalance-collection
  "Kicks off collection rebalancing. Will run synchronously if synchronous is true. Throws exceptions
  from failures to change the index set."
  [context concept-id synchronous]
  ;; This will throw an exception if the collection is already rebalancing
  (index-set/add-rebalancing-collection context indexer-index-set/index-set-id concept-id)
  ;; Clear the cache so that the newest index set data will be used.
  (indexer/clear-cache context)
  (let [provider-id (:provider-id (concepts/parse-concept-id concept-id))]
   ;; queue the collection for reindexing into the new index
   (index-collection context provider-id concept-id synchronous (keyword concept-id))))

(defn finalize-rebalance-collection
  "Finalizes collection rebalancing."
  [context concept-id]
  ;; This will throw an exception if the collection is not rebalancing
  (index-set/finalize-rebalancing-collection context indexer-index-set/index-set-id concept-id)
  ;; Clear the cache so that the newest index set data will be used.
  (indexer/clear-cache context)
  ;; There is a race condition as noted here: https://wiki.earthdata.nasa.gov/display/CMR/Rebalancing+Collection+Indexes+Approach
  ;; "There's a period of time during which the different indexer applications may be processing
  ;; granules for this very collection and may have already decided which index its going to. It's
  ;; possible that the indexer will index a granule into small collections after the bootstrap has
  ;; issued the delete. The next step to verify should identify if the race conditions has occurred. "
  ;; The sleep here decreases the probability of the race condition giving time for
  ;; indexer to finish indexing any granule currently being processed.
  ;; This doesn't remove the race condition. We still have steps in the overall process to detect it
  ;; and resolve it. (manual fixes if necessary)
  (Thread/sleep 5000)
  ;; Remove all granules from small collections for this collection.
  (rebalance-util/delete-collection-granules-from-small-collections context concept-id))



(defn rebalance-status
  "Returns a map of counts of granules in the collection in metadata db, the small collections index,
   and in the separate collection index if it exists."
  [context concept-id]
  (rebalance-util/rebalancing-collection-counts context concept-id))
