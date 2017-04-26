(ns cmr.search.site.util
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.common-app.api-docs :as api-docs]
   [cmr.search.site.data :as data]
   [ring.util.response :as ring-util]
   [selmer.parser :as selmer]))

(defn render-template
  "A utility function for preparing templates."
  ([context template]
    (render-template context template (data/base-page context)))
  ([context template page-data]
    (ring-util/response
     (selmer/render-file template page-data))))

(defn render-html
  "A utility function for preparing HTML templates."
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

(defn generate-docs
  "A utility function for rendering CMR search docs using Selmer."
  [site-title page-title md-source template-file out-file]
  (api-docs/generate page-title
                     md-source
                     out-file
                     (fn []
                      (selmer/render-file
                        template-file
                        {:base-url "../../"
                         :site-title site-title
                         :page-title page-title
                         :page-content (api-docs/md->html (slurp md-source))}))))
