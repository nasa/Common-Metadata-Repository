(ns cmr.ingest.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.ingest.site.data :as data]
   [cmr.common-app.site.pages :as common-pages]))

(defn home
  "Prepare the home page template."
  [context]
  (common-pages/render-html
   context
   "templates/ingest-home.html"
   (data/base-page context)))

(defn ingest-docs
  "Prepare the top-level ingest docs page."
  [context]
  (common-pages/render-html
   context
   "templates/ingest-docs.html"
   (data/base-page context)))

(defn ingest-site-docs
  "Prepare the site-specific (non-API) docs page."
  [context]
  (common-pages/render-html
   context
   "templates/ingest-site-docs.html"
   (data/base-page context)))
