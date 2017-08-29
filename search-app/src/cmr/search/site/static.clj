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

(def supported-directory-tags
  ["gov.nasa.eosdis"])

(defn generate-api-docs
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

(defn generate-site-docs
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

(defn generate-site-resources
  "Generate CMR Search site resources such as directory pages and XML sitemaps
  that are too expensive to generate dynamically."
  []
  (let [context {:cmr-application :search
                 :execution-context :cli
                 :tags supported-directory-tags}
        app-base-path (util/get-search-app-abs-path)]
    (debug "Created context for static generation:" context)
    (site/generate-top-level-resources context app-base-path
    (directory/generate-directory-resources context app-base-path))))

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
    :site (do
            (generate-site-docs)
            (generate-site-resources))
    :all (do
           (-main :prep)
           (-main :api)
           (-main :site))))
