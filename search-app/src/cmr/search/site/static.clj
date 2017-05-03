(ns cmr.search.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.common-app.api-docs :as api-docs]
   [cmr.search.site.data :as data])
  (:gen-class))

(defn generate-api-docs
  "Generate CMR Search API docs."
  []
  (api-docs/generate
   "resources/public/site/docs/search/api.html"
   "templates/search-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Search"
     :page-title "API Documentation"
     :page-content (api-docs/md-file->html "docs/api.md")})))

(defn generate-site-docs
  "Generate CMR Search docs for routes and web resources."
  []
  (api-docs/generate
   "resources/public/site/docs/search/site.html"
   "templates/search-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Search"
     :page-title "Site Routes & Web Resource Documentation"
     :page-content (api-docs/md-file->html "docs/site.md")})))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:

  $ lein run -m cmr.search.site.static prep
  $ lein run -m cmr.search.site.static api
  $ lein run -m cmr.search.site.static site
  $ lein run -m cmr.search.site.static all"
  [doc-type]
  (case (keyword doc-type)
    :prep (api-docs/prepare-docs)
    :api (generate-api-docs)
    :site (generate-site-docs)
    :all (do
          (-main :prep)
          (-main :api)
          (-main :site))))
