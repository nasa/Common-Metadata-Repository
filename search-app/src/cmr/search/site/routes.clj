(ns cmr.search.site.routes
  "This namespace is the one responsible for the routes intended for human
  consumption."
  (:require
    ;; Third-party libs
    [compojure.core :refer :all]
    [ring.util.response :refer [redirect]]
    [ring.swagger.ui :as ring-swagger-ui]

    ;; CMR libs
    [cmr.collection-renderer.api.routes :as collection-renderer-routes]
    [cmr.common.api.context :as context]
    [cmr.common-app.api-docs :as api-docs]
    [cmr.search.site.pages :as pages]))

(defn- redir-url
  "A utility function that creates a redirect URL with the given URL parts so
  that it works in dev and prod deployments."
  [base-url url-path]
  (if (empty? base-url)
    (str "/" url-path)
    (str base-url url-path)))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
        ;; Directory pages - Note that the directory pages must come before the
        ;; api-docs, since api-docs/docs-routes also use a context of "site"
        ;; but have the last entry as a 404 renderer, and as such, would
        ;; prevent any pages in the "site" context from rendering after that
        ;; point.
        (GET "/" request
          (pages/home request))
        (GET "/site/docs" request
          (pages/search-docs request))
        ;; XXX Eventually we will have better-organized docs resources; until
        ;; then, let's redirect to where they are.
        (GET "/site/docs/api" request
          (redirect
            (redir-url relative-root-url "site/search_api_docs.html")
            307))
        (GET "/site/docs/site" request
          (redirect (redir-url relative-root-url "site/search_site_docs.html")
            307))
        (GET "/site/collections/directory" request
          (pages/collections-directory request))
        (GET "/site/collections/directory/eosdis" request
          (pages/eosdis-collections-directory request))
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
