(ns cmr.ingest.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.common-app.api-docs :as api-docs]
   [cmr.ingest.site.data :as data])
  (:gen-class))

(defn generate-api-docs
  "Generate CMR Ingest API docs."
  []
  (api-docs/generate
   "resources/public/site/docs/ingest/api.html"
   "templates/ingest-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Ingest"
     :page-title "API Documentation"
     :page-content (api-docs/md-file->html "docs/api.md")})))

(defn generate-site-docs
  "Generate CMR Ingest docs for routes and web resources."
  []
  (api-docs/generate
   "resources/public/site/docs/ingest/site.html"
   "templates/ingest-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Ingest"
     :page-title "Site Routes & Web Resource Documentation"
     :page-content (api-docs/md-file->html "docs/site.md")})))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:
  ```
  $ lein run -m cmr.ingest.site.static api
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
