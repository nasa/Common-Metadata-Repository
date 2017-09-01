(ns cmr.search.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.search.site.static.directory :as directory]
   [cmr.search.site.static.site :as site]
   [cmr.search.site.util :as util]
   [cmr.transmit.config :as transmit])
  (:gen-class))

;; Contextual data that is used for static content in the absense of a system
;; context, e.g., when run from the command line.
(defrecord StaticContext [
  ;; the application which is being run with the context, e.g., :search
  cmr-application
  ;; the context of execution, e.g., :cli, when run from a system shell
  execution-context
  ;; a place to store context-specific data
  static-data])

(defn- generate-api-docs
  "Generate CMR Search API docs."
  []
  (static/generate
   "resources/public/site/docs/search/api.html"
   "templates/search-docs-static.html"
   (merge (data/base-page {:cmr-application :search
                           :execution-context :cli})
          {:site-title "CMR Search"
           :page-title "API Documentation"
           :page-content (static/md-file->html "docs/api.md")})))

(defn- generate-site-docs
  "Generate CMR Search docs for routes and web resources."
  []
  (static/generate
   "resources/public/site/docs/search/site.html"
   "templates/search-docs-static.html"
   (merge (data/base-page {:cmr-application :search
                           :execution-context :cli})
          {:site-title "CMR Search"
           :page-title "Site Routes & Web Resource Documentation"
           :page-content (static/md-file->html "docs/site.md")})))

(defn- generate-site-resources
  "Generate filesystem files for CMR Search site resources such as directory
  pages and XML sitemaps that are too expensive to generate dynamically.

  The results of this function are not used in production, but are useful
  when quickly checking on content generation without having to go poking
  around in the cache."
  []
  (let [context (map->StaticContext {:cmr-application :search
                                     :execution-context :cli})
        app-base-path (util/get-search-app-abs-path)]
    (debug "Created context for static generation:" context)
    (site/generate-top-level-resources context app-base-path)
    (directory/generate-directory-resources
      context app-base-path (keys util/supported-directory-tags))))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:

  $ lein run -m cmr.search.site.static prep
  $ lein run -m cmr.search.site.static api
  $ lein run -m cmr.search.site.static site
  $ lein run -m cmr.search.site.static all"
  [doc-type]
  (case (keyword doc-type)
    :prep (static/prepare-docs)
    :api (generate-api-docs)
    :site (generate-site-docs)
    :static-site (generate-site-resources)
    :all (do
           (-main :prep)
           (-main :api)
           (-main :site)
           (-main :static-site))))
