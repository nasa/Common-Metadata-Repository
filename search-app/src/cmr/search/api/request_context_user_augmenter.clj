(ns cmr.search.api.request-context-user-augmenter
 "Adds data to the context for the current user for performance improvements. Adds user id and sids
 as well as caches for those. "
 (:require
  [cmr.acl.core :as acl]
  [cmr.common.cache :as cache]
  [cmr.common.cache.in-memory-cache :as mem-cache]
  [cmr.common.log :refer (info)]
  [cmr.common.services.errors :as errors]
  [cmr.common.util :as util]
  [cmr.search.data.sids-retriever :as sids-retriever]
  [cmr.transmit.echo.tokens :as tokens]))

(def CACHE_TIME
 "The number of milliseconds token information will be cached for."
 (* 5 60 1000))

(def token-sid-cache-name
 :token-sid)

(def token-user-id-cache-name
 :token-user-id)

(defn create-token-sid-cache
 "Create a cache for sids by token"
 []
 (mem-cache/create-in-memory-cache :ttl {} {:ttl CACHE_TIME}))

(defn create-token-user-id-cache
 "Create a cache for user id by token"
 []
 (mem-cache/create-in-memory-cache :ttl {} {:ttl CACHE_TIME}))

(defn context->sids
  "Wraps the existing context->sids but with caching"
  [context]
  (let [{:keys [token]} context]
    (let [sids (cache/get-value (cache/context->cache context token-sid-cache-name)
                                token
                                #(or (sids-retriever/get-sids context token)
                                     {:sids (acl/context->sids context)}))
          token-guid (:guid sids)
          sids (:sids sids)]
      (when token-guid (info (format "Client token GUID: [%s]" token-guid)))
      sids)))

(defn context->user-id
 "Get the user id from the cache using the token. If there is a message for the token being required
 throw an exception if the token doesn't exist."
 ([context]
  (context->user-id context nil))
 ([context token-required-message]
  (if-let [token (:token context)]
    (if-let [cache (cache/context->cache context token-user-id-cache-name)]
     (cache/get-value cache
                     token
                     #(tokens/get-user-id context token))
     (tokens/get-user-id context token))
    (when token-required-message
     (errors/throw-service-error :unauthorized token-required-message)))))

(defn- add-user-id-and-sids-to-context
  "Adds information to the context including the user is and sids. Lazy assoc with a delay so we don't
  do the expensive work until we need the sids or user id. This is called for every search api
  call, so don't want this to affect performance."
  [context params headers]
  (def context context)
  (-> context
      (util/lazy-assoc :sids (context->sids context))
      (util/lazy-assoc :user-id (context->user-id context))))

(defn add-user-id-and-sids-handler
  "This is a ring handler that adds the authentication token and client id to the request context.
  It expects the request context is already associated with the request."
  [f]
  (fn [request]
    (let [{:keys [request-context params headers]} request]
      (f (update-in request [:request-context] add-user-id-and-sids-to-context params headers)))))
