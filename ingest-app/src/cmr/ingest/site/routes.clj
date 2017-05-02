(ns cmr.ingest.site.routes
  "Defines the HTTP URL routes for the ingest web site."
  (:require
   [cmr.common-app.api-docs :as api-docs]
   [cmr.common-app.site.data :as common-data]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.ingest.site.pages :as pages]
   [cmr.transmit.config :as config]
   [compojure.core :refer [GET context routes]]
   [ring.util.response :refer [redirect]]))

(defn build-routes [system]
  (routes
    (context (get-in system [:ingest-public-conf :relative-root-url]) []
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
      (api-docs/docs-routes
       (get-in system [:ingest-public-conf :protocol])
       (get-in system [:ingest-public-conf :relative-root-url])))))
