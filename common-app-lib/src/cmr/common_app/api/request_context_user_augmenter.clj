(ns cmr.common-app.api.request-context-user-augmenter
  "Adds data to the context for the current user for performance improvements.
  Adds user id and sids as well as caches for those. Data on the context is
  lazy assoc'd to delay expensive work since this is done for every call that
  hits the search API.

  Note: for any CMR applications that need to make use of the request-
  augmenting Ring handler in this namespace, that project's system namespace
  needs to be updated to include the two caches defined below when it builds
  the system datastructure."
  (:require
   [cheshire.core :as json]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.log :refer (info)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.access-control :as access-control]
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

(defn request-sids
  "Gets the current sids from access control and parses the returned json into a seq."
  [context]
  (let [{:keys [token]} context]
    (if token
      (->
        (access-control/get-current-sids context (:token context))
        json/parse-string)
      [:guest])))

(defn- context->sids
  "Wraps the existing context->sids but with caching"
  [context]
  (let [{:keys [token]} context]
    (cache/get-value (cache/context->cache context token-sid-cache-name)
                     token
                     #(request-sids context))))

(defn- context->user-id
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

(defn add-user-id-and-sids-to-context
  "Adds information to the context including the user id and sids. Lazy assoc with a delay so we don't
  do the expensive work until we need the sids or user id. This is called for every search api
  call, so don't want this to affect performance."
  [context]
  (-> context
      (util/lazy-assoc :sids (context->sids context))
      (util/lazy-assoc :user-id (context->user-id context))))

(defn add-user-id-and-sids-handler
  "This is a ring handler that adds the authentication token and client id to the request context.
  It expects the request context is already associated with the request."
  [handler]
  (fn [request]
    (let [{:keys [request-context]} request]
      (-> request
          (update-in [:request-context] add-user-id-and-sids-to-context)
          (handler)))))
