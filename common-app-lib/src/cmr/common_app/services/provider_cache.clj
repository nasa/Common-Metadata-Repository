(ns cmr.common-app.services.provider-cache
  "Uses the redis hash cache to access providers in redis. This class 
  provides shared functions for provider data that the CMR apps use."
  (:require
   [clojure.set :as set]
   [cmr.common.hash-cache :as hcache]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as rhcache]
   [cmr.transmit.metadata-db2 :as mdb]))

(def cache-key
  "Identifies the key used when the cache is stored in the system."
  :provider-cache)

(defn create-cache
  "Creates a client instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)}))

(defn- provider-does-not-exist
  [provider-id]
  (format "Provider with provider-id [%s] does not exist." (util/html-escape provider-id)))

(defn refresh-provider-cache
  "Refreshes the provider data stored in the cache. This should be called from a background job on a
  timer to keep the cache fresh. The providers data coming from the database or if they are passed in looks like:
  '({:provider-id \"PROV1\" :short-name \"PROV1\" :cmr-only false :small false :consortiums \"EOSDIS GEOSS\"}
    {:provider-id \"PROV2\" :short-name \"PROV2\" :cmr-only false :small false :consortiums \"EOSDIS GEOSS\"})
   The provider map going into the set-values funtion looks like the following:
   {\"PROV1\" {:provider-id \"PROV1\" :short-name \"PROV1\" :cmr-only false :small false :consortiums \"EOSDIS GEOSS\"}
    \"PROV2\" {:provider-id \"PROV2\" :short-name \"PROV2\" :cmr-only false :small false :consortiums \"EOSDIS GEOSS\"}}"
  ([context]
   (refresh-provider-cache context (mdb/get-providers context)))
  ([context providers]
   (rl-util/log-refresh-start cache-key)
   (let [cache (hcache/context->cache context cache-key)
         provider-map (into {} (map (fn [provider]
                                      {(:provider-id provider) provider})
                                    providers))
         [tm result] (util/time-execution
                      (hcache/set-values cache cache-key provider-map))]
     (rl-util/log-redis-write-complete "refresh-provider-cache" cache-key tm)
     result)))

(defn- get-cached-providers
  "Retrieve the list of all providers from the cache. Setting the value
  if not present."
  [context]
  (rl-util/log-redis-reading-start "get-cached-providers get-map" cache-key)
  (let [cache (hcache/context->cache context cache-key)
        [tm result] (util/time-execution (hcache/get-map cache cache-key))
        _ (rl-util/log-redis-read-complete "get-cached-providers" cache-key tm)]
    (if result
      result
      (let [_ (refresh-provider-cache context)
            [tm rst] (util/time-execution (hcache/get-map cache cache-key))]
        (rl-util/log-redis-read-complete "get-cached-providers" cache-key tm)
        rst))))

(defn validate-providers-exist
  "Throws an exception if the given provider-ids are invalid, otherwise returns the input."
  [context providers]
  (when-let [invalid-providers
             (seq (set/difference
                   (set providers)
                   (set (map :provider-id (vals (get-cached-providers context))))))]
    (errors/throw-service-errors
     :bad-request
     (map provider-does-not-exist invalid-providers)))
  providers)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing the KMS keywords cache. Only one node needs to refresh the cache.
(def-stateful-job RefreshProviderCacheJob
  [_ system]
  (refresh-provider-cache {:system system}))

(defn refresh-provider-cache-job
  "The singleton job that refreshes the provider cache."
  [job-key]
  {:job-type RefreshProviderCacheJob
   :job-key job-key
   ;; The time is UTC time.
   :daily-at-hour-and-minute [07 00]})
