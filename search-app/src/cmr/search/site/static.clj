(ns cmr.search.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.common-app.api-docs :as api-docs]
   [cmr.search.site.data :as data])
  (:gen-class))

;; XXX delete or refactor once ingest-app docs code is updated
(defn generate-docs
  "A utility function for rendering CMR search docs using templates."
  [site-title page-title md-source template-file out-file]
  (api-docs/generate page-title
                     md-source
                     out-file
                     template-file
                     (merge
                      (data/base-static)
                      {:site-title site-title
                       :page-title page-title
                       :page-content (api-docs/md->html (slurp md-source))})))

(defn generate-api-docs
  "Generate CMR Search API docs."
  []
  (generate-docs "CMR Search"
                 "API Documentation"
                 "docs/api.md"
                 "templates/search-docs-static.html"
                 "resources/public/site/docs/search/api.html"))

(defn generate-site-docs
  "Generate CMR Search docs for routes and web resources."
  []
  (generate-docs "CMR Search"
                 "Site Routes & Web Resource Documentation"
                 "docs/site.md"
                 "templates/search-docs-static.html"
                 "resources/public/site/docs/search/site.html"))

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
