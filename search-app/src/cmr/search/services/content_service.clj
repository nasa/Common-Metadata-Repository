(ns cmr.search.services.content-service
  "Provides service functions for generating static content."
  (:require
   [clojure.string :as string]
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
   [cmr.transmit.metadata-db :as mdb]))

(def cache-key
  "The key used to store the humanizer report cache in the system cache map."
  :static-content-cache)

(defconfig job-delay
  "Number of seconds the `static-content-generator-job` needs to wait after
  collection cache refresh job starts.

  We need to add the delay so that the collection cache can be populated first
  (collections are one of the key things queried when generating static
  content)."
  {:default 400
   :type Long})

(defconfig job-wait
  "Number of milliseconds the `static-content-generator-job` waits for the
  collection cache to be populated in the event when the delay is not long
  enough."
  {:default (* 60 1000)
   :type Long})

(defconfig retry-count
  "Number of times `static-content-generator-job` retries to get the
  collections from collection cache."
  {:default 20
   :type Long})

(defconfig generation-interval
  "Number of seconds between `static-content-generator-job` runs."
  {;:default (* 60 60 24)
   :default (* 60)
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
   (cond
     (string/ends-with? route ".xml")
     (pages/sitemap-provider-tag context provider-id tag)
     :else (pages/provider-tag-directory context provider-id tag))))

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

(defn retrieve-page
  "Attempt to retrieve from the cache the page data for a given route. If no
  value is found in the cache, genereate the content, cache it, and then return
  the cached value."
  [& args]
    (let [context (first args)
          route (second args)]
      (debug "Retrieving" route)
      (if-let [content (cache/get-value (cache/context->cache context cache-key)
                                        route)]
        content
        (do
          (debug "Route" route "not found in cache")
          (apply cache-page args)
          (retrieve-page context route)))))

(defn- cache-top-level-content
  "Cache all the content for the expensive, top-level routes."
  [context]
  (cache-page context "/sitemap.xml")
  (cache-page context "/site/sitemap.xml")
  (cache-page context "/site/collections/directory/eosdis"))

(defn- cache-directory-content
  "Cache all the content for the expensive, directory-level routes."
  [context]
  (debug "Providers:" (mdb/get-providers context))
  (debug "Provider ids:" (map :provider-id (mdb/get-providers context)))
  (doseq [provider-id (map :provider-id (mdb/get-providers context))]
    (doseq [tag (keys site-utils/supported-directory-tags)]
      (doseq [route [(format "/site/collections/directory/%s/%s" provider-id tag)
                     (format "/site/collections/directory/%s/%s/sitemap.xml"
                             provider-id
                             tag)]]
        (cache-page context route provider-id tag)))))

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
(defjob StaticContentGeneratorJob
  [_job-context system]
  (generate-content {:system system}))

(def static-content-generator-job
  "The job definition used by the system job scheduler."
  {:job-type StaticContentGeneratorJob
   :interval (generation-interval)
   :start-delay (+ (default-job-start-delay) (job-delay))})
