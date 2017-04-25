(ns cmr.search.site.util
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.search.site.data :as data]
   [ring.util.response :as ring-util]
   [selmer.parser :as selmer]))

(defn render-template
  "A utility function for preparing template pages."
  ([context template]
    (render-template context template (data/base-page context)))
  ([context template page-data]
    (ring-util/response
     (selmer/render-file template page-data))))

(defn render-html
  "A utility function for preparing template pages."
  ([context template]
    (render-html context template (data/base-page context)))
  ([context template page-data]
    (ring-util/content-type
     (render-template context template page-data)
     "text/html")))

(defn render-xml
  "A utility function for preparing XML templates."
  ([context template]
    (render-xml context template (data/base-page context)))
  ([context template page-data]
    (ring-util/content-type
     (render-template context template page-data)
     "text/xml")))
