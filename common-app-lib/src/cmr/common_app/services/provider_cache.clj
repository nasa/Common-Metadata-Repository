(ns cmr.common-app.services.provider-cache
  "Uses the redis hash cache to access providers in redis. This class 
  provides shared functions for provider data that the CMR apps use."
  (:require
   [clojure.set :as cset]
   [cmr.common.hash-cache :as hcache]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.common.log :as log :refer [info]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.redis-utils.redis-hash-cache :as rhcache]
   [cmr.transmit.metadata-db2 :as mdb]))

(def cache-key
  "Identifies the key used when the cache is stored in the system."
  :provider-cache)

(defn create-cache
  "Creates a client instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [cache-key]}))

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
   (let [cache (hcache/context->cache context cache-key)
         provider-map (into {} (map (fn [provider]
                                      {(:provider-id provider) provider})
                                    providers))
         start (System/currentTimeMillis)
         _ (info "Refreshing provider cache - saving to redis.")
         result (hcache/set-values cache cache-key provider-map)]
     (info (format "Redis timed function refresh-provider-cache for %s redis set-values time [%s] ms " cache-key (- (System/currentTimeMillis) start)))
     (info "Refreshed provider cache.")
     result)))

(defn- get-cached-providers
  "Retrieve the list of all providers from the cache. Setting the value
  if not present."
  [context]
  (info "Reading provider cache - all of it.")
  (let [cache (hcache/context->cache context cache-key)
        start (System/currentTimeMillis)
        providers (or (hcache/get-map cache cache-key)
                      (do
                        (refresh-provider-cache context)
                        (hcache/get-map cache cache-key)))]
    (info (format "Redis timed function get-cached-providers for %s redis get-map time [%s] ms " cache-key (- (System/currentTimeMillis) start)))
    providers))
    
    ;(or (hcache/get-map cache cache-key)
    ;    (do 
    ;      (refresh-provider-cache context)
    ;      (hcache/get-map cache cache-key)))))

(defn validate-providers-exist
  "Throws an exception if the given provider-ids are invalid, otherwise returns the input."
  [context providers]
  (when-let [invalid-providers
             (seq (cset/difference
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
