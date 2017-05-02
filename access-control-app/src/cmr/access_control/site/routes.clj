(ns cmr.access-control.site.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cmr.access-control.site.pages :as pages]
   [cmr.common-app.api-docs :as api-docs]
   [cmr.common-app.site.data :as common-data]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.common.api.context :as context]
   [cmr.transmit.config :as config]
   [compojure.core :refer :all]
   [ring.util.response :refer [redirect]]))

(defn build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (GET "/"
           {context :request-context}
           (pages/home context))
      (GET "/site/docs"
           {context :request-context}
           (pages/access-control-docs context))
      ;; Support better organization of documentation URLs and support old
      ;; URLs
      (GET "/site/docs/api"
           {context :request-context}
           (redirect
            (str (config/application-public-root-url context)
                 "site/docs/api.html")
            307))
      (GET "/site/docs/acl-usage"
           {context :request-context}
           (redirect
            (str (config/application-public-root-url context)
                 "site/docs/acl-usage.html")
            307))
      (GET "/site/docs/acl-schema"
           {context :request-context}
           (redirect
            (str (config/application-public-root-url context)
                 "site/docs/acl-schema.html")
            307))
      (GET "/site/docs/site"
           {context :request-context}
           (redirect
            (str (config/application-public-root-url context)
                "site/docs/site.html")
            307))
      (GET "/site/access_control_api_docs.html"
           {context :request-context}
           (redirect
            (str (config/application-public-root-url context)
                 "site/docs/api.html")
            301))
      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:public-conf :protocol])
                            (get-in system [:public-conf :relative-root-url])))))
