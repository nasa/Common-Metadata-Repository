(ns cmr.search.services.content-service
  "Provides service functions for generating static content."
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer [debug]]
   [cmr.search.site.pages :as pages]))

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

(defn retrieve-page
  "Attempt to retrieve from the cache the page data for a given route. If no
  value is found in the cache, genereate the content, cache it, and then return
  the cached value.

  Note that the first three arguements will always be `context`, HTTP `params`,
  and resource `route`. Additional arguments may be present (e.g., provider id
  and tag)."
  [context params route & remaining]
  (debug "Retrieving" route "with params" params "and remaining args" remaining)
  (let [args (concat [context route] remaining)]
    (apply get-page-content args)))
