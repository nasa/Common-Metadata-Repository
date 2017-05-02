(ns cmr.access-control.site.pages
  (:require
   [cmr.access-control.site.data :as data]
   [cmr.common-app.site.pages :as common-pages]))

(defn home
  "Prepare the home page template."
  [context]
  (common-pages/render-html
   context
   "templates/access-control-home.html"
   (data/base-page context)))

(defn access-control-docs
  "Prepare the top-level access control docs page."
  [context]
  (common-pages/render-html
   context
   "templates/access-control-docs.html"
   (data/base-page context)))

(defn access-control-site-docs
  "Prepare the site-specific (non-API) docs page."
  [context]
  (common-pages/render-html
   context
   "templates/access-control-site-docs.html"
   (data/base-page context)))
