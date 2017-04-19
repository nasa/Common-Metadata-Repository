(ns cmr.search.site.routes
  "This namespace is the one responsible for the routes intended for human
  consumption."
  (:require
   [cmr.collection-renderer.api.routes :as collection-renderer-routes]
   [cmr.common.api.context :as context]
   [cmr.common-app.api-docs :as api-docs]
   [cmr.search.site.pages :as pages]
   [cmr.transmit.config :as config]
   [compojure.core :refer :all]
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
        (GET "/site/docs"
             {context :request-context}
             (pages/search-docs context))
        ;; XXX Eventually we will have better-organized docs resources; until
        ;; then, let's redirect to where they are.
        (GET "/site/docs/api"
             {context :request-context}
             (redirect
               (str (config/application-public-root-url context)
                    "site/search_api_docs.html")
               307))
        (GET "/site/docs/site"
             {context :request-context}
             (redirect
               (str (config/application-public-root-url context)
                    "site/search_site_docs.html")
               307))
        (GET "/site/collections/directory"
             {context :request-context}
             (pages/collections-directory context))
        (GET "/site/collections/directory/eosdis"
             {context :request-context}
             (pages/eosdis-collections-directory context))
        ;; Add routes for API documentation
        (api-docs/docs-routes
         (get-in system [:public-conf :protocol])
         relative-root-url
         "public/index.html")
        (ring-swagger-ui/swagger-ui
         "/swagger_ui"
         :swagger-docs (str relative-root-url "/site/swagger.json")
         :validator-url nil)
        ;; Routes for collection html resources such as css, js, etc.
        (collection-renderer-routes/resource-routes system)))))
