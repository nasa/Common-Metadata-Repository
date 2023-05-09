(ns cmr.transmit.launchpad-user-cache
  "Contains code for managing the launchpad user cache.  We want to cache the launchpad token expiration date and username for
  launchpad tokens to avoid needing to call out to EDL for authentication every time.  Launchpad tokens have a lifetime currently of 60 minutes."
  (:require
   [clj-time.core :as t]
   [clj-time.format :as t-format]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.transmit.urs :as urs]))

(def launchpad-user-cache-key
  "The cache key for a launchpad token cache."
  :launchpad-user)

(def LAUNCHPAD_USER_CACHE_TIME
  "The number of milliseconds launchpad token information will be cached."
  3600000)

(defn create-launchpad-user-cache
  "Creates a cache for which launchpad token users are stored in memory."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl LAUNCHPAD_USER_CACHE_TIME}))

(defn get-launchpad-user

  "Get the launchpad user from the cache, uses token as key on miss.
  Expiration time is calculated via the response from EDL and saved in the cache."
  [context token]
  (let [get-launchpad-user-fn (fn []
                                (when-let [resp (urs/get-launchpad-user context token)]
                                  (assoc resp :expiration-time (-> (or (:lp-token-expires-in resp)
                                                                       LAUNCHPAD_USER_CACHE_TIME)
                                                                   (t/seconds)
                                                                   (t/from-now)))))]
    (if-let [cache (cache/context->cache context launchpad-user-cache-key)]
      (let [token-info (cache/get-value cache (keyword token) get-launchpad-user-fn)]
        (if (t/before? (:expiration-time token-info) (time-keeper/now))
          (get-launchpad-user-fn)
          token-info))
      (get-launchpad-user-fn))))
