(ns cmr.bootstrap.services.bootstrap-service
  "Provides methods to insert migration requets on the approriate channels."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.bootstrap.data.bulk-index :as bulk]
   [cmr.bootstrap.data.rebalance-util :as rebalance-util]
   [cmr.bootstrap.embedded-system-helper :as helper]
   [cmr.bootstrap.services.dispatch.core :as dispatch]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.rebalancing-collections :as rebalancing-collections]
   [cmr.common.services.errors :as errors]
   [cmr.indexer.data.index-set :as indexer-index-set]
   [cmr.indexer.system :as indexer-system]
   [cmr.transmit.indexer :as indexer]))

(def request-type->dispatcher
  "A map of request types to which dispatcher to use for asynchronous requests."
  {:migrate-provider :core-async-dispatcher
   :migrate-collection :core-async-dispatcher
   :index-provider :message-queue-dispatcher
   :index-variables :message-queue-dispatcher
   :index-services :message-queue-dispatcher
   :index-tools :message-queue-dispatcher
   :index-subscriptions :message-queue-dispatcher
   :index-generics :message-queue-dispatcher
   :index-data-later-than-date-time :message-queue-dispatcher
   :index-collection :core-async-dispatcher
   :index-system-concepts :core-async-dispatcher
   :index-concepts-by-id :core-async-dispatcher
   :fingerprint-by-id :synchronous-dispatcher
   :fingerprint-variables :message-queue-dispatcher
   :delete-concepts-from-index-by-id :core-async-dispatcher
   :bootstrap-virtual-products :core-async-dispatcher})

(defn migrate-provider
  "Copy all the data for a provider (including collections and graunules) from catalog rest
  to the metadata db without blocking."
  [context dispatcher provider-id]
  (dispatch/migrate-provider dispatcher context provider-id))

(defn migrate-collection
  "Copy all the data for a given collection (including graunules) from catalog rest
  to the metadata db without blocking."
  [context dispatcher provider-id collection-id]
  (dispatch/migrate-collection dispatcher context provider-id collection-id))

(defn- get-provider
  "Returns the metadata db provider that matches the given provider id. Throws exception if
  no matching provider is found."
  [context provider-id]
  (if-let [provider (helper/get-provider (:system context) provider-id)]
    provider
    (errors/throw-service-errors
     :bad-request
     [(format "Provider: [%s] does not exist in the system" provider-id)])))

(defn validate-collection
   "Validates to be bulk_indexed collection exists in cmr else an exception is thrown."
   [context provider-id collection-id]
   (let [provider (get-provider context provider-id)]
     (when-not (bulk/get-collection context provider collection-id)
       (errors/throw-service-errors
        :bad-request
        [(format "Collection [%s] does not exist." collection-id)]))))

(defn index-provider
  "Bulk index all the collections and granules for a provider."
  [context dispatcher provider-id start-index]
  (get-provider context provider-id)
  (dispatch/index-provider dispatcher context provider-id start-index))

(defn index-all-providers
  "Bulk index all the collections and granules for all providers."
  [context dispatcher]
  (info "Indexing all providers")
  (doseq [provider (helper/get-providers (:system context))
          :let [provider-id (:provider-id provider)]]
    (info (format "Processing provider [%s] for bulk indexing" provider-id))
    (index-provider context dispatcher provider-id 0))
  (info "Indexing of all providers scheduled/completed."))

(defn index-data-later-than-date-time
  "Bulk index all the concepts with a revision date later than the given date-time."
  [context dispatcher provider-ids date-time]
  (dispatch/index-data-later-than-date-time dispatcher context provider-ids date-time))

(defn index-collection
  "Bulk index all the granules in a collection"
  [context dispatcher provider-id collection-id options]
  (dispatch/index-collection dispatcher context provider-id collection-id options))

(defn index-system-concepts
  "Bulk index all the tags, acls, and access-groups."
  [context dispatcher start-index]
  (dispatch/index-system-concepts dispatcher context start-index))

(defn index-concepts-by-id
  "Bulk index the concepts given by the concept-ids"
  [context dispatcher provider-id concept-type concept-ids]
  (dispatch/index-concepts-by-id dispatcher context provider-id concept-type concept-ids))

(defn index-variables
  "(Re-)Index the variables stored in metadata-db. If a provider-id is passed,
  only the variables for that provider will be indexed. With no provider-id,
  all providers' variables are (re-)indexed."
  ([context dispatcher]
   (dispatch/index-variables dispatcher context))
  ([context dispatcher provider-id]
   (dispatch/index-variables dispatcher context provider-id)))

(defn index-services
  "(Re-)Index the services stored in metadata-db. If a provider-id is passed,
  only the services for that provider will be indexed. With no provider-id,
  all providers' services are (re-)indexed."
  ([context dispatcher]
   (dispatch/index-services dispatcher context))
  ([context dispatcher provider-id]
   (dispatch/index-services dispatcher context provider-id)))

(defn index-tools
  "(Re-)Index the tools stored in metadata-db. If a provider-id is passed,
  only the tools for that provider will be indexed. With no provider-id,
  all providers' tools are (re-)indexed."
  ([context dispatcher]
   (dispatch/index-tools dispatcher context))
  ([context dispatcher provider-id]
   (dispatch/index-tools dispatcher context provider-id)))

(defn index-subscriptions
  "(Re-)Index the subscriptions stored in metadata-db. If a provider-id is passed,
  only the subscriptions for that provider will be indexed. With no provider-id,
  all providers' subscriptions are (re-)indexed."
  ([context dispatcher]
   (dispatch/index-subscriptions dispatcher context))
  ([context dispatcher provider-id]
   (dispatch/index-subscriptions dispatcher context provider-id)))

(defn index-generics
  "(Re-)Index the generic documents stored in metadata-db. If a provider-id is passed,
  only the generic documents for that provider will be indexed. With no provider-id,
  all providers' generic documents are (re-)indexed."
  ([context dispatcher concept-type provider-id]
   (if provider-id
    (dispatch/index-generics dispatcher context concept-type provider-id)
    (dispatch/index-generics dispatcher context concept-type))))

(defn delete-concepts-from-index-by-id
  "Bulk delete the concepts given by the concept-ids from the indexes"
  [context dispatcher provider-id concept-type concept-ids]
  (dispatch/delete-concepts-from-index-by-id dispatcher context provider-id concept-type
                                                      concept-ids))

(defn bootstrap-virtual-products
  "Initializes virtual products."
  [context dispatcher provider-id entry-title]
  (dispatch/bootstrap-virtual-products dispatcher context provider-id entry-title))

(defn fingerprint-by-id
  "Update the fingerprint of the given variable if necessary."
  [context dispatcher concept-id]
  (info "Updating fingerprint for" concept-id)
  (dispatch/fingerprint-by-id dispatcher context concept-id))

(defn fingerprint-variables
  "Update the fingerprint of the variables specified by the request body if necessary."
  [context dispatcher body]
  (if-let [provider-id (get body "provider_id")]
    (dispatch/fingerprint-variables dispatcher context provider-id)
    (dispatch/fingerprint-variables dispatcher context)))

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
  from failures to change the index set. If no target is specified default to moving to a separate
  index."
  [context dispatcher concept-id target]
  (let [target (or target "separate-index")]
    (info (format "Starting to rebalance granules for collection [%s] to target [%s]."
                  concept-id target))
    (rebalancing-collections/validate-target target concept-id)
    (when (= "separate-index" target)
      (validate-collection context (:provider-id (concepts/parse-concept-id concept-id)) concept-id))
    ;; This will throw an exception if the collection is already rebalancing
    (indexer/add-rebalancing-collection context indexer-index-set/index-set-id concept-id
                                        (csk/->kebab-case-keyword target))

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
       {:target-index-key (if (= "small-collections" target)
                            :small_collections
                            (keyword concept-id))
        :completion-message (format "Completed reindex of [%s] for rebalancing granule indexes."
                                    concept-id)
        :rebalancing-collection? true}))))

(defn finalize-rebalance-collection
  "Finalizes collection rebalancing."
  [context concept-id]
  (let [fetched-index-set (indexer/get-index-set context indexer-index-set/index-set-id)
        target (get-in fetched-index-set [:index-set :granule :rebalancing-targets (keyword concept-id)])]
    (info (format "Finalizing rebalancing granules for collection [%s] to target [%s]."
                  concept-id target))
    (rebalancing-collections/validate-target target concept-id)
    ;; This will throw an exception if the collection is not rebalancing
    (indexer/finalize-rebalancing-collection context indexer-index-set/index-set-id concept-id)
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
    (when (= "separate-index" target)
      (rebalance-util/delete-collection-granules-from-small-collections context concept-id))))

(defn rebalance-status
  "Returns a map of counts of granules in the collection in metadata db, the small collections index,
   and in the separate collection index if it exists."
  [context concept-id]
  (assoc
   (rebalance-util/rebalancing-collection-counts context concept-id)
   :rebalancing-status
   (rebalance-util/rebalancing-collection-status context concept-id)))
