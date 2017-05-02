(ns cmr.access-control.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.access-control.site.data :as data]
   [cmr.common-app.api-docs :as api-docs])
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
  "Generate CMR Access Control API docs."
  []
  (generate-docs "CMR Access Control"
                 "API Documentation"
                 "docs/api.md"
                 "templates/access-control-docs-static.html"
                 "resources/public/site/docs/access-control/api.html"))

(defn generate-acl-schema-docs
  "Generate CMR Access Control API docs."
  []
  (generate-docs "CMR Access Control"
                 "ACL Schema"
                 "docs/acl-schema.md"
                 "templates/access-control-docs-static.html"
                 "resources/public/site/docs/access-control/schema.html"))

(defn generate-acl-usage-docs
  "Generate CMR Access Control API docs."
  []
  (generate-docs "CMR Access Control"
                 "Using ACLS in the CMR"
                 "docs/acl-usage.md"
                 "templates/access-control-docs-static.html"
                 "resources/public/site/docs/access-control/usage.html"))

(defn generate-site-docs
  "Generate CMR Search docs for routes and web resources."
  []
  (generate-docs "CMR Access Control"
                 "Site Routes & Web Resource Documentation"
                 "docs/site.md"
                 "templates/access-control-docs-static.html"
                 "resources/public/site/docs/access-control/site.html"))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:
  ```
  $ lein run -m cmr.access-control.site.static api
  $ lein run -m cmr.access-control.site.static acl-schema
  $ lein run -m cmr.access-control.site.static acl-usage
  $ lein run -m cmr.access-control.site.static site
  $ lein run -m cmr.access-control.site.static all
  ```"
  [doc-type]
  (case (keyword doc-type)
    :api (generate-api-docs)
    :acl-schema (generate-acl-schema-docs)
    :acl-usage (generate-acl-usage-docs)
    :site (generate-site-docs)
    :all (do
          (-main :api)
          (-main :acl-schema)
          (-main :acl-usage)
          (-main :site))))
