(ns cmr.ingest.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.common-app.api-docs :as api-docs]
   [cmr.ingest.site.data :as data])
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
  "Generate CMR ingest API docs."
  []
  (generate-docs "CMR Ingest"
                 "API Documentation"
                 "docs/api.md"
                 "templates/ingest-docs-static.html"
                 "resources/public/site/docs/ingest/api.html"))

(defn generate-site-docs
  "Generate CMR Ingest docs for routes and web resources."
  []
  (generate-docs "CMR Access Control"
                 "Site Routes & Web Resource Documentation"
                 "docs/site.md"
                 "templates/ingest-docs-static.html"
                 "resources/public/site/docs/ingest/site.html"))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:
  ```
  $ lein run -m cmr.ingest.site.static api
  $ lein run -m cmr.ingest.site.static acl-schema
  $ lein run -m cmr.ingest.site.static acl-usage
  $ lein run -m cmr.ingest.site.static site
  $ lein run -m cmr.ingest.site.static all
  ```"
  [doc-type]
  (case (keyword doc-type)
    :api (generate-api-docs)
    :site (generate-site-docs)
    :all (do
          (-main :api)
          (-main :site))))
