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
