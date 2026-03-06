(ns cmr.transmit.launchpad-user-cache
  "Contains code for managing the launchpad user cache.  We want to cache the launchpad token expiration date and username for
  launchpad tokens to avoid needing to call out to EDL for authentication every time.  Launchpad tokens have a lifetime currently of 60 minutes."
  (:require
   [clj-time.core :as t]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
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

(def transient-error-types
  "Error types that should not be cached - they are transient and should be retried immediately"
  #{:too-many-requests :gateway-timeout})

(defn get-launchpad-user
  "Get the launchpad user from cache or EDL.
  Most errors are cached for 5 minutes then retried, preventing repeated requests with bad tokens.
  Transient errors (429, 504) are not cached and passed through immediately."
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
                                    (let [error-type (:type (ex-data e))
                                          error-msg (.getMessage e)]
                                      ;; Don't cache transient errors - rethrow immediately
                                      (if (transient-error-types error-type)
                                        (errors/throw-service-error error-type error-msg)
                                        {:valid false
                                         :error-message error-msg
                                         :error-type error-type
                                         :expiration-time (t/plus (time-keeper/now) (t/minutes (transmit-config/invalid-token-timeout)))})))))]
    (if-let [cache (cache/context->cache context launchpad-user-cache-key)]
      (let [cache-key (keyword (str (hash token)))
            token-info (cache/get-value cache cache-key get-launchpad-user-fn)]
        (if (t/before? (:expiration-time token-info) (time-keeper/now))
          ;; Cache entry expired (after 5 min for errors, or token lifetime for valid tokens)
          ;; Evict and retry EDL
          (do
            (cache/evict cache cache-key)
            (let [fresh-result (get-launchpad-user-fn)]
              (if (:valid fresh-result)
                (do
                  (cache/set-value cache cache-key fresh-result)
                  fresh-result)
                (errors/throw-service-error
                 (or (:error-type fresh-result) :unauthorized)
                 (or (:error-message fresh-result)
                     (format "Invalid Launchpad token (partially redacted) [%s]"
                             (common-util/scrub-token token)))))))
          ;; Cache entry still valid - return cached result or throw cached error
          (if (:valid token-info)
            token-info
            (errors/throw-service-error
             (or (:error-type token-info) :unauthorized)
             (or (:error-message token-info)
                 (format "Invalid Launchpad token (partially redacted) [%s]"
                         (common-util/scrub-token token)))))))
      (let [result (get-launchpad-user-fn)]
        (if (:valid result)
          result
          (errors/throw-service-error
           (or (:error-type result) :unauthorized)
           (or (:error-message result)
               (format "Invalid Launchpad token (partially redacted) [%s]"
                       (common-util/scrub-token token)))))))))
