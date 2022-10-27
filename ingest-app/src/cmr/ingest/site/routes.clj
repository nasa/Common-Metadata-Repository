(ns cmr.ingest.site.routes
  "Defines the HTTP URL routes for the ingest web site."
  (:require
   [cmr.common-app.static :as static]
   [cmr.common-app.site.data :as common-data]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.ingest.site.pages :as pages]
   [cmr.transmit.config :as config]
   [compojure.core :refer [GET context routes]]
   [ring.util.response :refer [redirect]]))

;;The spacer function calculates the number of spaces used by headers
;; in the table of contents for generic documents specific by document type
(def options {:spacer #(case %
                         2 0
                         3 8
                         4 4
                         0)})

(defn build-routes [system]
  (routes
   (context (get-in system [:public-conf :relative-root-url]) []
     (GET "/"
       {ctx :request-context}
       (pages/home ctx))
      ;; Backwards comapatibility for old docs URLs
     (GET "/site/ingest_api_docs.html"
       {ctx :request-context}
       (redirect
        (str (config/application-public-root-url ctx)
             "site/docs/ingest/api.html")
        301))
      ;; Access control docs context
     (context "/site/docs/ingest" []
       (GET "/"
         {ctx :request-context}
         (pages/ingest-docs ctx))
        ;; Support better organization of documentation URLs and support old
        ;; URLs
       (GET "/api"
         {ctx :request-context}
         (redirect
          (str (config/application-public-root-url ctx)
               "site/docs/ingest/api.html")
          307))
       (GET "/site"
         {ctx :request-context}
         (redirect
          (str (config/application-public-root-url ctx)
               "site/docs/ingest/site.html")
          307)))
      ;; Add routes for general API documentation
     (static/docs-routes
      (get-in system [:public-conf :protocol])
      (get-in system [:public-conf :relative-root-url])
      options))))
