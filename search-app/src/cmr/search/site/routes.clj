(ns cmr.search.site.routes
  "This namespace is the one responsible for the routes intended for human
  consumption."
  (:require
   ;; Third-party libs
   [compojure.core :refer :all]
   [ring.swagger.ui :as ring-swagger-ui]
   [selmer.parser :as selmer]

   ;; CMR libs
   [cmr.collection-renderer.api.routes :as collection-renderer-routes]
   [cmr.common.api.context :as context]
   [cmr.common-app.api-docs :as api-docs]
   [cmr.search.site.data :as data]))

(defn render-template-ok
  ""
  [template data]
  {:status 200
   :body (selmer/render-file template data)})

(defn landing-links-page
  ""
  [request]
  (render-template-ok
    "templates/landing-links.html"
    (data/get-landing-links request)))

(defn eosdis-landing-links-page
  ""
  [request]
  (render-template-ok
    "templates/eosdis-landing-links.html"
    (data/get-eosdis-landing-links request)))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
        ;; Landing pages - Note that the landing pages must come before the
        ;; api-docs, since api-docs/docs-routes also use a context of "site"
        ;; but have the last entry as a 404 renderer, and as such, would
        ;; prevent any pages in the "site" context from rendering after that
        ;; point.
        (GET "/site/collections/landing-pages" request
          (landing-links-page request))
        (GET "/site/collections/eosdis-landing-pages" request
          (eosdis-landing-links-page request))
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
