(ns cmr.search.site.routes
  "This namespace is the one responsible for the routes intended for human
  consumption (via a web browser)."
  (:require
   [cmr.collection-renderer.api.routes :as collection-renderer-routes]
   [cmr.common.api.context :as context]
   [cmr.common-app.api-docs :as api-docs]
   [cmr.search.site.pages :as pages]
   [cmr.transmit.config :as config]
   [compojure.core :as compojure :refer [GET context routes]]
   [ring.swagger.ui :as ring-swagger-ui]
   [ring.util.response :refer [redirect]]))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
        ;; Directory pages - Note that the directory pages must come before the
        ;; api-docs, since api-docs/docs-routes also use a context of "site"
        ;; but have the last entry as a 404 renderer, and as such, would
        ;; prevent any pages in the "site" context from rendering after that
        ;; point.
        (GET "/"
             {context :request-context}
             (pages/home context))
        (GET "/sitemap.xml"
             {context :request-context}
             (pages/sitemap-master context))
        (GET "/site/sitemap.xml"
             {context :request-context}
             (pages/sitemap-top-level context))
        (GET "/site/docs"
             {context :request-context}
             (pages/search-docs context))
        ;; Support better organization of documentation URLs and support old
        ;; URLs
        (GET "/site/docs/api"
             {context :request-context}
             (redirect
              (str (config/application-public-root-url context)
                   "site/docs/api.html")
              307))
        (GET "/site/docs/site"
             {context :request-context}
             (redirect
              (str (config/application-public-root-url context)
                   "site/docs/site.html")
              307))
        (GET "/site/search_api_docs.html"
             {context :request-context}
             (redirect
              (str (config/application-public-root-url context)
                   "site/docs/api.html")
              301))
        (GET "/site/search_site_docs.html"
             {context :request-context}
             (redirect
              (str (config/application-public-root-url context)
                   "site/docs/site.html")
              301))
        (GET "/site/collections/directory"
             {context :request-context}
             (pages/collections-directory context))
        (GET "/site/collections/directory/eosdis"
             {context :request-context}
             (pages/eosdis-collections-directory context))
        (GET "/site/collections/directory/:provider-id/:tag"
             [provider-id tag :as {context :request-context}]
             (pages/provider-tag-directory context provider-id tag))
        (GET "/site/collections/directory/:provider-id/:tag/sitemap.xml"
             [provider-id tag :as {context :request-context}]
             (pages/sitemap-provider-tag context provider-id tag))
        ;; Add routes for API documentation
        (api-docs/docs-routes
         (get-in system [:public-conf :protocol])
         relative-root-url)
        (ring-swagger-ui/swagger-ui
         "/swagger_ui"
         :swagger-docs (str relative-root-url "/site/swagger.json")
         :validator-url nil)
        ;; Routes for collection html resources such as css, js, etc.
        (collection-renderer-routes/resource-routes system)))))
