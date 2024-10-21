(ns cmr.metadata-db.services.subscription-cache
  "Defines common functions and defs for the subscription cache.
	Structure of the hash-cache is as follows:
	<collection-concept-id> --> <ingest subscription map>

	Example:
	'C12344554-PROV1' --> { 'enabled' true|false
                          'mode'    New|Update|Delete|All} "
  (:require
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]))

(def subscription-cache-key
  "The cache key to use when storing with caches in the system."
  "subscription-cache")

(defn create-cache-client
  "Creates an instance of the cache."
  []
  (redis-hash-cache/create-redis-hash-cache {:keys-to-track [subscription-cache-key]
                                             :read-connection (redis-config/redis-read-conn-opts)
                                             :primary-connection (redis-config/redis-conn-opts)}))

(defn set-value
  "Set the collection concept id and its subscription map described at the top."
  [context field value]
  (let [cache-client (hash-cache/context->cache context subscription-cache-key)]
    (hash-cache/set-value cache-client subscription-cache-key field value)))

(defn get-value
  "Returns the collection-concept-id subscription map which is described at the top."
  [context collection-concept-id]
  (let [cache-client (hash-cache/context->cache context subscription-cache-key)
        [tm value] (util/time-execution
                    (hash-cache/get-value cache-client subscription-cache-key collection-concept-id))]
    (rl-util/log-redis-read-complete "ingest-subscription-cache get-value" subscription-cache-key tm)
    value))

(defn remove-value
  "Removes the collection-concept-id and its subscription map."
  [context collection-concept-id]
  (let [cache-client (hash-cache/context->cache context subscription-cache-key)
        [tm value] (util/time-execution
                    (hash-cache/remove-value cache-client subscription-cache-key collection-concept-id))]
    (rl-util/log-redis-write-complete "ingest-subscription-cache remove-value" subscription-cache-key tm)
    value))
