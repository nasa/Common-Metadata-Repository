(ns cmr.common-app.api.enabled
  "Defines functions and routes for enabling and disabling an application
  as well as checking whether or not an application is enabled. Disabled can
  mean different things for different apps. Disabled ingest prevents
  writes, but otherwise allows operations to proceed. This functionality is
  provided to support the migration to NGAP. We need to be able to prevent
  anything from writing on the NGAP side while we are transitioning."
  (:require
   [cheshire.core :as json]
   [cmr.common.api.context :as context]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [error warn]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [compojure.core :refer [context GET POST]]))

(def write-enabled-cache-key
  "The key used to store the write enable state cache in the system cache map."
  :write-enabled)

(defconfig write-enabled-cache-time-seconds
  "The number of seconds to cache the state of the application."
  {:default 5
   :type Long})

(defn create-write-enabled-cache
  "Creates the enabled cache. We cache the enabled response for applications for a few seconds to
  prevent many enabled checks over a short time period to cause significant load on the system."
  []
  (stl-cache/create-single-thread-lookup-cache
    (consistent-cache/create-consistent-cache {:hash-timeout-seconds (write-enabled-cache-time-seconds)})))

(defn service-write-disabled-message
  "Creates a message indicating that the given service is disabled."
  [service]
  (format "The %s service is disabled for writes." service))

(defn app-write-enabled?
  "Returns true if the application is enabled, false otherwise. We use a cache to prevent enabled?
  check calls."
  [context]
  (if-let [cache (cache/context->cache context write-enabled-cache-key)]
   (cache/get-value cache write-enabled-cache-key (constantly true))
   (do
     (warn "Application enabled state is not being cached.")
     ;; All apps are enabled until explicitly disabled.
     true)))

(defn- app-write-enabled-response
  "Creates an appropriate response for /write-enabled queries."
  [request-context]
  {:status 200
   :headers {"Content-Type" (mt/with-utf-8 mt/json)}
   :body (json/generate-string (if (app-write-enabled? request-context) "Enabled" "Disabled"))})

(defn validate-write-enabled
  "Validate that the app is enabled for writes. Throws a service error if not."
  [context service]
  (when-not (app-write-enabled? context)
    (errors/throw-service-error :service-unavailable (service-write-disabled-message service))))

(defn- write-disable-app
  "Mark the app given by the context as write disabled."
  [context]
  (if-let [cache (cache/context->cache context write-enabled-cache-key)]
    (do (cache/set-value cache write-enabled-cache-key false)
        {:status 200
         :headers {"Content-Type" (mt/with-utf-8 mt/json)}
         :body (json/generate-string "Application successfully disabled.")})
    (do
      (error "Application enabled state is not being cached. Could not disable application.")
      {:status 500
       :headers {"Content-Type" (mt/with-utf-8 mt/json)}
       :body (json/generate-string "'Enabled' cache is not available.")})))

(defn- write-enable-app
  "Mark the app given by the context as write enabled."
  [context]
  (if-let [cache (cache/context->cache context write-enabled-cache-key)]
    (do (cache/set-value cache write-enabled-cache-key true)
        {:status 200
         :headers {"Content-Type" (mt/with-utf-8 mt/json)}
         :body (json/generate-string "Application successfully enabled.")})
    (do
      (error "Application write-enabled state is not being cached. Could not write-enable application.")
      {:statud 500
       :headers {"Content-Type" (mt/with-utf-8 mt/json)}
       :body (json/generate-string "'Enabled' cache is not available.")})))

(defn write-enabled-api-routes
  "Creates common routes for checking and manipulating the enabled/disabled state of an app.
  permission-check-fn is a function of one argument (the request context)
  to call to verify the user has permission to enable/disable the service."
  [permission-check-fn]
  (context "/" []
    (GET "/write-enabled" {:keys [request-context]}
      (app-write-enabled-response request-context))
    (POST "/enable-writes" {:keys [request-context]}
      (permission-check-fn request-context)
      (write-enable-app request-context))
    (POST "/disable-writes" {:keys [request-context]}
      (permission-check-fn request-context)
      (write-disable-app request-context))))
