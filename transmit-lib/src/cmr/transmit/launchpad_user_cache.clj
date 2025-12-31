(ns cmr.transmit.launchpad-user-cache
  "Contains code for managing the launchpad user cache.  We want to cache the launchpad token expiration date and username for
  launchpad tokens to avoid needing to call out to EDL for authentication every time.  Launchpad tokens have a lifetime currently of 60 minutes."
  (:require
   [clj-time.core :as t]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.log :refer [error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.urs :as urs]))

(def launchpad-user-cache-key
  "The cache key for a launchpad token cache."
  :launchpad-user)

(def LAUNCHPAD_USER_CACHE_TIME
  "The number of milliseconds launchpad token information will be cached."
  (* 60 60 common-util/second-as-milliseconds))

(defn create-launchpad-user-cache
  "Creates a cache for which launchpad token users are stored in memory."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl LAUNCHPAD_USER_CACHE_TIME}))

(defn get-launchpad-user
  "Get the launchpad user from the cache, uses token as key on miss.
  Expiration time is calculated via the response from EDL and saved in the cache.
  Failed validations are also cached to prevent repeated requests with bad tokens."
  [context token]
  (let [get-launchpad-user-fn (fn []
                                (try
                                  (let [resp (urs/get-launchpad-user context token)]
                                    (assoc resp
                                           :expiration-time (-> (or (:lp_token_expires_in resp)
                                                                    (/ LAUNCHPAD_USER_CACHE_TIME 1000))
                                                                (t/seconds)
                                                                (t/from-now))
                                           :valid true))
                                  (catch Exception e
                                    {:valid false
                                     :error-message (.getMessage e)
                                     :expiration-time (t/plus (time-keeper/now) (t/minutes (transmit-config/invalid-token-timeout)))})))]
    (if-let [cache (cache/context->cache context launchpad-user-cache-key)]
      (let [token-info (cache/get-value cache (keyword (str (hash token))) get-launchpad-user-fn)]
        (if (t/before? (:expiration-time token-info) (time-keeper/now))
          (do
            (error (format "Launchpad token (partially redacted) [%s] has expired."
                           (common-util/scrub-token token)))
            (errors/throw-service-error
             :unauthorized
             (format "Launchpad token (partially redacted) [%s] has expired."
                     (common-util/scrub-token token))))
          (if (:valid token-info)
            token-info
            (errors/throw-service-error
             :unauthorized
             (or (:error-message token-info)
                 (format "Invalid Launchpad token (partially redacted) [%s]"
                         (common-util/scrub-token token)))))))
      (let [result (get-launchpad-user-fn)]
        (if (:valid result)
          result
          (errors/throw-service-error
           :unauthorized
           (or (:error-message result)
               (format "Invalid Launchpad token (partially redacted) [%s]"
                       (common-util/scrub-token token)))))))))
