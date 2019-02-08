(ns cmr.access-control.site.routes
  "Defines the HTTP URL routes for the access-control web site."
  (:require
   [cmr.access-control.site.pages :as pages]
   [cmr.common-app.site.data :as common-data]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.common-app.static :as static]
   [cmr.transmit.config :as config]
   [compojure.core :refer [GET context routes]]
   [ring.util.response :refer [redirect]]))

(defn build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (GET "/"
           {ctx :request-context}
           (pages/home ctx))
      ;; Backwards comapatibility for old docs URLs
      (GET "/site/access_control_api_docs.html"
           {ctx :request-context}
           (redirect
            (str (config/application-public-root-url ctx)
                 "site/docs/access-control/api.html")
            301))
      ;; Access control docs context
      (context "/site/docs/access-control" []
        (GET "/"
             {ctx :request-context}
             (pages/access-control-docs ctx))
        ;; Support better organization of documentation URLs and support old
        ;; URLs
        (GET "/api"
             {ctx :request-context}
             (redirect
              (str (config/application-public-root-url ctx)
                   "site/docs/access-control/api.html")
              307))
        (GET "/usage"
             {ctx :request-context}
             (redirect
              (str (config/application-public-root-url ctx)
                   "site/docs/access-control/usage.html")
              307))
        (GET "/schema"
             {ctx :request-context}
             (redirect
              (str (config/application-public-root-url ctx)
                   "site/docs/access-control/schema.html")
              307))
        (GET "/site"
             {ctx :request-context}
             (redirect
              (str (config/application-public-root-url ctx)
                  "site/docs/access-control/site.html")
              307)))
      ;; Add routes for general API documentation
      (static/docs-routes
       (get-in system [:public-conf :protocol])
       (get-in system [:public-conf :relative-root-url])))))
