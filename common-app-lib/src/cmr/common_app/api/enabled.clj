(ns cmr.common-app.api.enabled
  "Defines the enabled routes for applications."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common.api.context :as context]
   [cmr.common-app.cache.consistent-cache :as consistent-cache]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [error warn]]
   [cmr.common.mime-types :as mt]
   [compojure.core :refer [context GET POST]]))

(def enabled-cache-key
  "The key used to store the enable state cache in the system cache map."
  :enabled)

(defconfig enabled-cache-time-seconds
  "The number of seconds to cache the health of the application."
  {:default 50
   :type Long})

(defn create-enabled-cache
  "Creates the enabled cache. We cache the enabled response for applications for a few seconds to
  prevent many enabled checks over a short time period to cause significant load on the system."
  []
  (stl-cache/create-single-thread-lookup-cache
    (consistent-cache/create-consistent-cache {:hash-timeout-seconds (enabled-cache-time-seconds)})))

; (defn expire-consistent-cache-hashes
;   "Forces the cached hash codes of a consistent cache to expire so that subsequent requests for
;    enabled state will check cubby for consistency."
;   [context]
;   (let [cache (cache/context->cache context enabled-cache-key)]
;     (consistent-cache/expire-hash-cache-timeouts (:delegate-cache cache))))

(defn service-disabled-message
  "Creates a message indicating that the given service is disabled."
  [service]
  (format "The %s service is disabled." service))

(defmulti generate-disabled-response
  "Generates an error response for when the caller tries to access a disabled application."
  (fn [response-format application]
    response-format))

(defn app-enabled?
  "Returns true if the application is enabled, false otherwise. We use a cache to prevent enabled?
  check calls. Enabled can mean different things for different apps. Disabled ingest prevents 
  writes, but otherwise allows operations to proceed."
  [context]
  (if-let [cache (cache/context->cache context enabled-cache-key)]
   (cache/get-value cache enabled-cache-key (constantly true))
   (do
     (warn "Application enabled state is not being cached.")
     ;; All apps are enabled until explicitly disabled.
     true)))

(defn- app-enabled-response
  "Creates an appropriate response for /enabled queries."
  [request-context]
  {:status 200 
   :headers {"Content-Type" (mt/with-utf-8 mt/json)}
   :body (json/generate-string (if (app-enabled? request-context) "Enabled" "Disabled"))})

(defn- disable-app
  "Mark the  app given by the context as disabled."
  [context]
  (if-let [cache (cache/context->cache context enabled-cache-key)]
    (do (cache/set-value cache enabled-cache-key false)
        ;; (expire-consistent-cache-hashes context)
        {:status 200 
         :headers {"Content-Type" (mt/with-utf-8 mt/json)}
         :body (json/generate-string "Application successfully disabled.")})
    (do
      (error "Application enabled state is not being cached. Could not disable application.")
      {:status 500 
       :headers {"Content-Type" (mt/with-utf-8 mt/json)}
       :body (json/generate-string "'Enabled' cache is not available.")})))

(defn- enable-app
  "Mark the app given by the context as enabled."
  [context]
  (if-let [cache (cache/context->cache context enabled-cache-key)]
    (do (cache/set-value cache enabled-cache-key true)
        ;; (expire-consistent-cache-hashes context)
        {:status 200 
         :headers {"Content-Type" (mt/with-utf-8 mt/json)}
         :body (json/generate-string "Application successfully enabled.")})
    (do
      (error "Application enabled state is not being cached. Could not enable application.")
      {:statud 500 
       :headers {"Content-Type" (mt/with-utf-8 mt/json)}
       :body (json/generate-string "'Enabled' cache is not available.")})))

(defn enabled-api-routes
  "Creates common routes for checking and manipulating the enabled/disabled state of an app.
  permission-check-fn is a function of one argument (the request context)
  to call to verify the user has permission to enable/disable the service."
  [permission-check-fn]
  (context "/" []
    (GET "/enabled" {:keys [request-context]}
      (app-enabled-response request-context))
    (POST "/enable" {:keys [request-context]}
      (permission-check-fn request-context)
      (enable-app request-context))
    (POST "/disable" {:keys [request-context]}
      (permission-check-fn request-context)
      (disable-app request-context))))
