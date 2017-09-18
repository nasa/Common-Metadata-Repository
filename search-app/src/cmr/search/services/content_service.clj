(ns cmr.search.services.content-service
  "Provides service functions for generating static content."
  (:require
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.cache.consistent-cache :as consistent-cache]
   [cmr.common-app.cache.cubby-cache :as cubby-cache]
   [cmr.common.cache :as cache]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob default-job-start-delay]]
   [cmr.common.log :refer :all]
   [cmr.common.util :as util]
   [cmr.search.site.pages :as pages]
   [cmr.search.site.static :as static]
   [cmr.search.site.util :as site-utils]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.metadata-db :as mdb]))

(def cache-key
  "The key used to store the static content cache in the system cache map."
  :static-content-cache)

(defconfig static-content-generation-job-delay
  "Number of seconds the `static-content-generator-job` should wait after
  collection cache refresh job starts."
  {:default 0
   :type Long})

(defconfig static-content-generation-interval
  "Number of seconds between `static-content-generator-job` runs."
  {:default (* 60 60 24)
   :type Long})

(defn create-cache
  "This function creates the composite cache that is used for caching the
  static content data. With the given composition we get the following
  features:
  * A cubby cache that holds the generated content (centralized storage in
    an ElasticSearch backend);
  * A single-threaded cache that circumvents potential race conditions
    between HTTP requests for a report and Quartz cluster jobs that save
    report data.

  This function is intended to be called by a function that sets up caches for
  an application (e.g., cmr.search.system/create-system)."
  []
  (stl-cache/create-single-thread-lookup-cache
   (cubby-cache/create-cubby-cache)))

(defn get-page-content
  "Given a context and route (optionally provider id and tag), get the
  output of the page template that corresponds to the route."
  ([context route]
   (case route
     "/sitemap.xml" (pages/sitemap-master context)
     "/site/sitemap.xml" (pages/sitemap-top-level context)
     "/site/collections/directory/eosdis" (pages/eosdis-collections-directory context)))
  ([context route provider-id tag]
   (if (string/ends-with? route ".xml")
     (pages/sitemap-provider-tag context provider-id tag)
     (pages/provider-tag-directory context provider-id tag))))

(defn cache-page
  "Cache a page for a given route. If the content is supplied, simply cache
  that; if only a route is supplied, get the content for that route and then
  cache it. If a provider id and tag are supplied, get the appopriate content
  given those args, and cache the result."
  ([context route]
   (cache-page context route (get-page-content context route)))
  ([context route content]
   (debug "Caching" route)
   (cache/set-value (cache/context->cache context cache-key) route content))
  ([context route provider-id tag]
    (cache-page context
                route
                (get-page-content context route provider-id tag))))

(defn- create-lookup-fn
  "A convenience function that is intended for use by the caching API when a
  cache miss is encountered."
  [args]
  (debug "Route" (second args) "not found in cache")
  (fn [] (apply get-page-content args)))

(defn retrieve-page
  "Attempt to retrieve from the cache the page data for a given route. If no
  value is found in the cache, genereate the content, cache it, and then return
  the cached value.

  Note that the first three arguements will always be `context`, HTTP `params`,
  and resource `route`. Additional arguments may be present (e.g., provider id
  and tag)."
  [context params route & remaining]
  (debug "Retrieving" route "with params" params "and remaining args" remaining)
  (let [args (concat [context route] remaining)
        regenerate? (= "true" (util/safe-lowercase (:regenerate params)))]
    ;; Only admins can force the pages and sitemaps in the cache to be
    ;; regenerated.
    (when regenerate?
      (acl/verify-ingest-management-permission context :update)
      (debug "Forcing resource regeneration/cache update" route)
      (apply cache-page args))
    (cache/get-value (cache/context->cache context cache-key)
                     route
                     (create-lookup-fn args))))

(defn- cache-top-level-content
  "Cache all the content for the expensive, top-level routes."
  [context]
  (cache-page context "/sitemap.xml")
  (cache-page context "/site/sitemap.xml")
  (cache-page context "/site/collections/directory/eosdis"))

(defn- cache-directory-content
  "Cache all the content for the expensive, directory-level routes."
  [context]
  (let [provider-ids (map :provider-id (mdb/get-providers context))]
    (debug "Providers:" provider-ids)
    (doseq [provider-id provider-ids]
      (doseq [tag (keys site-utils/supported-directory-tags)]
        (doseq [route [(format "/site/collections/directory/%s/%s" provider-id tag)
                       (format "/site/collections/directory/%s/%s/sitemap.xml"
                               provider-id
                               tag)]]
          (cache-page context route provider-id tag))))))

(defn generate-content
  "Cache all the content for the expensive routes. This is the function
  intended for use by the static content job scheduler."
  [context]
  (info "Generating site content for caching")
  (let [[ms _] (util/time-execution
                (do
                  (cache-top-level-content context)
                  (cache-directory-content context)))]
    (info (format "Content generated in %d ms" ms))))

;; A job for generating static content
;; Note: since content generation is being done via a job without an HTTP
;;       context, we use the :cli execution context that doesn't require
;;       particular ACL settings provided by the HTTP request context.
(defjob StaticContentGeneratorJob
  [_job-context system]
  (-> {:system system}
      (transmit-config/with-echo-system-token)
      (generate-content)))

(def static-content-generator-job
  "The job definition used by the system job scheduler."
  {:job-type StaticContentGeneratorJob
   :interval (static-content-generation-interval)
   :start-delay (+ (default-job-start-delay) (static-content-generation-job-delay))})
