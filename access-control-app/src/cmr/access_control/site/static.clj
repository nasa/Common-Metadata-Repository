(ns cmr.access-control.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.access-control.site.data :as data]
   [cmr.common-app.static :as static])
  (:gen-class))

(defn generate-api-docs
  "Generate CMR Access Control API docs."
  []
  (static/generate
   "resources/public/site/docs/access-control/api.html"
   "templates/access-control-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Access Control"
     :page-title "API Documentation"
     :page-content (static/md-file->html "docs/api.md")})))

(defn generate-acl-schema-docs
  "Generate CMR Access Control API docs."
  []
  (static/generate
   "resources/public/site/docs/access-control/schema.html"
   "templates/access-control-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Access Control"
     :page-title "ACL Schema Definitions"
     :page-content (static/md-file->html "docs/acl-schema.md")})))

(defn generate-acl-usage-docs
  "Generate CMR Access Control API docs."
  []
  (static/generate
   "resources/public/site/docs/access-control/usage.html"
   "templates/access-control-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Access Control"
     :page-title "Using ACLS in the CMR"
     :page-content (static/md-file->html "docs/acl-usage.md")})))

(defn generate-site-docs
  "Generate CMR Access Control docs for routes and web resources."
  []
  (static/generate
   "resources/public/site/docs/access-control/site.html"
   "templates/access-control-docs-static.html"
   (merge
    (data/base-static)
    {:site-title "CMR Access Control"
     :page-title "Site Routes & Web Resource Documentation"
     :page-content (static/md-file->html "docs/site.md")})))

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
