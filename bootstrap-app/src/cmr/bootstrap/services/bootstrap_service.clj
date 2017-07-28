(ns cmr.bootstrap.services.bootstrap-service
  "Provides methods to insert migration requets on the approriate channels."
  (:require
    [cmr.bootstrap.data.bulk-index :as bulk]
    [cmr.bootstrap.data.rebalance-util :as rebalance-util]
    [cmr.bootstrap.services.dispatch.dispatch-protocol :as dispatch-protocol]
    [cmr.common.cache :as cache]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.services.errors :as errors]
    [cmr.indexer.data.index-set :as indexer-index-set]
    [cmr.indexer.system :as indexer-system]
    [cmr.transmit.index-set :as index-set]))

(def request-type->dispatcher
  "A map of request types to which dispatcher to use for asynchronous requests."
  {:migrate-provider :core-async-dispatcher
   :migrate-collection :core-async-dispatcher
   :index-provider :core-async-dispatcher
   :index-data-later-than-date-time :core-async-dispatcher
   :index-collection :core-async-dispatcher
   :index-system-concepts :core-async-dispatcher
   :index-concepts-by-id :core-async-dispatcher
   :delete-concepts-from-index-by-id :core-async-dispatcher
   :bootstrap-virtual-products :core-async-dispatcher})

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [context dispatcher provider-id]
  (dispatch-protocol/migrate-provider dispatcher context provider-id))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [context dispatcher provider-id collection-id]
  (dispatch-protocol/migrate-collection dispatcher context provider-id collection-id))

(defn- get-provider
  "Returns the metadata db provider that matches the given provider id. Throws exception if
  no matching provider is found."
  [context provider-id]
  (if-let [provider (bulk/get-provider-by-id context provider-id)]
    provider
    (errors/throw-service-errors :bad-request
                              [(format "Provider: [%s] does not exist in the system" provider-id)])))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [context dispatcher provider-id start-index]
  (get-provider context provider-id)
  (dispatch-protocol/index-provider dispatcher context provider-id start-index))

(defn index-data-later-than-date-time
  "Bulk index all the concepts with a revision date later than the given date-time."
  [context dispatcher date-time]
  (dispatch-protocol/index-data-later-than-date-time dispatcher context date-time))

(defn- validate-collection
  "Validates to be bulk_indexed collection exists in cmr else an exception is thrown."
  [context provider-id collection-id]
  (let [provider (get-provider context provider-id)]
    (when-not (bulk/get-collection context provider collection-id)
      (errors/throw-service-errors :bad-request
                                [(format "Collection [%s] does not exist." collection-id)]))))

(defn index-collection
  "Bulk index all the granules in a collection"
  ([context dispatcher provider-id collection-id]
   (index-collection context dispatcher provider-id collection-id nil))
  ([context dispatcher provider-id collection-id options]
   (validate-collection context provider-id collection-id)
   (dispatch-protocol/index-collection dispatcher context provider-id collection-id options)))

(defn index-system-concepts
  "Bulk index all the tags, acls, and access-groups."
  [context dispatcher start-index]
  (dispatch-protocol/index-system-concepts dispatcher context start-index))

(defn index-concepts-by-id
  "Bulk index the concepts given by the concept-ids"
  [context dispatcher provider-id concept-type concept-ids]
  (dispatch-protocol/index-concepts-by-id dispatcher context provider-id concept-type concept-ids))

(defn delete-concepts-from-index-by-id
  "Bulk delete the concepts given by the concept-ids from the indexes"
  [context dispatcher provider-id concept-type concept-ids]
  (dispatch-protocol/delete-concepts-from-index-by-id dispatcher context provider-id concept-type
                                                      concept-ids))

(defn bootstrap-virtual-products
  "Initializes virtual products."
  [context dispatcher provider-id entry-title]
  (dispatch-protocol/bootstrap-virtual-products dispatcher context provider-id entry-title))

(defn- wait-until-index-set-hash-cache-times-out
  "Waits until the indexer's index set cache hash codes times out so that all of the indexer's will
   be using the same cached data."
  []
  ;; Wait 3 seconds beyond the time that the indexer set cache consistency setting.
  (let [sleep-secs (+ 3 (indexer-system/index-set-cache-consistent-timeout-seconds))]
    (info "Waiting" sleep-secs "seconds so indexer index set hashes will timeout.")
    (Thread/sleep (* 1000 sleep-secs))))

(defn start-rebalance-collection
  "Kicks off collection rebalancing. Will run synchronously if synchronous is true. Throws exceptions
  from failures to change the index set."
  [context dispatcher concept-id]
  (validate-collection context (:provider-id (concepts/parse-concept-id concept-id)) concept-id)
  ;; This will throw an exception if the collection is already rebalancing
  (index-set/add-rebalancing-collection context indexer-index-set/index-set-id concept-id)

  ;; Clear the cache so that the newest index set data will be used.
  ;; This clears embedded caches so the indexer cache in this bootstrap app will be cleared.
  (cache/reset-caches context)

  ;; We must wait here so that any new granules coming in will start to pick up the new index set
  ;; and be indexed into both the old and the new. Then we can safely reindex everything and know
  ;; we haven't missed a granule. There would be a race condition otherwise where a new granule
  ;; came in and was indexed only to the old collection but after we started reindexing the collection.
  (wait-until-index-set-hash-cache-times-out)

  (let [provider-id (:provider-id (concepts/parse-concept-id concept-id))]
    ;; queue the collection for reindexing into the new index
    (index-collection
     context dispatcher provider-id concept-id
     {:target-index-key (keyword concept-id)
      :completion-message (format "Completed reindex of [%s] for rebalancing granule indexes."
                                  concept-id)})))

(defn finalize-rebalance-collection
  "Finalizes collection rebalancing."
  [context concept-id]
  (validate-collection context (:provider-id (concepts/parse-concept-id concept-id)) concept-id)
  ;; This will throw an exception if the collection is not rebalancing
  (index-set/finalize-rebalancing-collection context indexer-index-set/index-set-id concept-id)
  ;; Clear the cache so that the newest index set data will be used.
  ;; This clears embedded caches so the indexer cache in this bootstrap app will be cleared.
  (cache/reset-caches context)

  ;; There is a race condition as noted here: https://wiki.earthdata.nasa.gov/display/CMR/Rebalancing+Collection+Indexes+Approach
  ;; "There's a period of time during which the different indexer applications may be processing
  ;; granules for this very collection and may have already decided which index its going to. It's
  ;; possible that the indexer will index a granule into small collections after the bootstrap has
  ;; issued the delete. The next step to verify should identify if the race conditions has occurred. "
  ;; The sleep here decreases the probability of the race condition giving time for
  ;; indexer to finish indexing any granule currently being processed.
  ;; This doesn't remove the race condition. We still have steps in the overall process to detect it
  ;; and resolve it. (manual fixes if necessary)
  (wait-until-index-set-hash-cache-times-out)

  ;; Remove all granules from small collections for this collection.
  (rebalance-util/delete-collection-granules-from-small-collections context concept-id))

(defn rebalance-status
  "Returns a map of counts of granules in the collection in metadata db, the small collections index,
   and in the separate collection index if it exists."
  [context concept-id]
  (rebalance-util/rebalancing-collection-counts context concept-id))
