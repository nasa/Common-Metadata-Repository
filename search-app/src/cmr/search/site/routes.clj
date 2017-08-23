(ns cmr.search.site.routes
  "This namespace is the one responsible for the routes intended for human
  consumption (via a web browser)."
  (:require
   [cmr.collection-renderer.api.routes :as collection-renderer-routes]
   [cmr.common-app.static :as static]
   [cmr.search.site.pages :as pages]
   [cmr.transmit.config :as config]
   [compojure.core :refer [GET context routes]]
   [ring.swagger.ui :as ring-swagger-ui]
   [ring.util.response :refer [redirect]]))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
        ;; Directory pages - Note that the directory pages must come before the
        ;; api-docs, since static/docs-routes also use a context of "site"
        ;; but have the last entry as a 404 renderer, and as such, would
        ;; prevent any pages in the "site" context from rendering after that
        ;; point.
        (GET "/"
             {ctx :request-context}
             (pages/home ctx))
        (GET "/site/collections/directory"
             {ctx :request-context}
             (pages/collections-directory ctx))
        ;; Backwards comapatibility for old docs URLs
        (GET "/site/search_api_docs.html"
             {ctx :request-context}
             (redirect
              (str (config/application-public-root-url ctx)
                   "site/docs/search/api.html")
              301))
        (GET "/site/search_site_docs.html"
             {ctx :request-context}
             (redirect
              (str (config/application-public-root-url ctx)
                   "site/docs/search/site.html")
              301))
        ;; Search docs context
        (context "/site/docs/search" []
          (GET "/"
               {ctx :request-context}
               (pages/search-docs ctx))
          (GET "/api"
               {ctx :request-context}
               (redirect
                (str (config/application-public-root-url ctx)
                     "site/docs/search/api.html")
                307))
          (GET "/site"
               {ctx :request-context}
               (redirect
                (str (config/application-public-root-url ctx)
                     "site/docs/search/site.html")
                307)))
        ;; Add routes for general API documentation
        (static/docs-routes
         (get-in system [:public-conf :protocol])
         relative-root-url)
        (ring-swagger-ui/swagger-ui
         "/swagger_ui"
         :swagger-docs (str relative-root-url "/site/swagger.json")
         :validator-url nil)
        ;; Routes for collection html resources such as css, js, etc.
        (collection-renderer-routes/resource-routes system)))))
