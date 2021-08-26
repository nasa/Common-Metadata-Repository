(ns cmr.common-app.api.health
  "Defines the health routes for applications."
  (:require
   [cheshire.core :as json]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [warn]]
   [cmr.common.mime-types :as mt]
   [compojure.core :refer :all]))

(def health-cache-key
  "The key used to store the health cache in the system cache map."
  :health)

(defconfig health-cache-time-seconds
  "The number of seconds to cache the health of the application."
  {:default 5
   :type Long})

(defn create-health-cache
  "Creates the health cache. We cache the health response for applications for a few seconds to
  prevent many health checks over a short time period to cause significant load on the system.
  This is most helpful for applications which are dependencies on several other applications such
  as metadata-db and redis."
  []
  (stl-cache/create-single-thread-lookup-cache
   (mem-cache/create-in-memory-cache :ttl {} {:ttl (* 1000 (health-cache-time-seconds))})))

(defn- generate-health-response
  "Generates a health check response from the provided health information."
  [{:keys [ok? dependencies]}]
  (when-not ok?
    (warn "Health check failed" (pr-str dependencies)))
  {:status (if ok? 200 503)
   :headers {common-routes/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)
             common-routes/CORS_ORIGIN_HEADER "*"}
   :body (json/generate-string dependencies)})

(defn- get-app-health
  "Returns the application's health response. We use a cache to prevent multiple health check calls
  especially from multiple application dependencies from having a cascading affect on the load of
  the system."
  [context health-fn]
  (if-let [cache (cache/context->cache context health-cache-key)]
    (generate-health-response (cache/get-value cache health-cache-key #(health-fn context)))
    (do
      (warn "Application health is not being cached.")
      (generate-health-response (health-fn context)))))

(defn health-api-routes
  "Creates common routes for checking the health of a CMR application. Takes a health-fn which
  takes a request-context as a parameter to determine if the application and its dependencies are
  working as expected."
  [health-fn]
  (context "/health" []
    (OPTIONS "/" req (common-routes/options-response))
    (GET "/" {:keys [request-context]}
      (get-app-health request-context health-fn))))
