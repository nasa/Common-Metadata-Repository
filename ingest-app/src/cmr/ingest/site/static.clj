(ns cmr.ingest.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.common-app.config :as common-config]
   [cmr.common-app.static :as static]
   [cmr.ingest.config :as ingest-config]
   [cmr.ingest.site.data :as data]
   [selmer.parser :as selmer])
  (:gen-class))

(defn generate-api-docs
  "Generate CMR Ingest API docs."
  []
  (let [collection-umm-version (common-config/collection-umm-version)
        granule-umm-version (ingest-config/granule-umm-version)
        variable-umm-version (ingest-config/variable-umm-version)
        service-umm-version (ingest-config/service-umm-version)
        tool-umm-version (ingest-config/tool-umm-version)
        subscription-umm-version (ingest-config/subscription-umm-version)]
    (static/generate
     "resources/public/site/docs/ingest/api.html"
     "templates/ingest-docs-static.html"
     (merge
      (data/base-static)
      {:site-title "CMR Ingest"
       :page-title "API Documentation"
       :page-content (selmer/render
                      (static/md-file->html "docs/api.md")
                      {:umm-c collection-umm-version
                       :umm-g granule-umm-version
                       :umm-s service-umm-version
                       :umm-t tool-umm-version
                       :umm-sub subscription-umm-version
                       :umm-var variable-umm-version})}))))

(defn generate-site-docs
  "Generate CMR Ingest docs for routes and web resources."
  []
  (static/generate
   "resources/public/site/docs/ingest/site.html"
   "templates/ingest-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Ingest"
     :page-title "Site Routes & Web Resource Documentation"
     :page-content (static/md-file->html "docs/site.md")})))

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
