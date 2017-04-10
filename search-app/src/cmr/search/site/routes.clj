(ns cmr.search.site.routes
  "This namespace is the one responsible for the routes intended for human
  consumption."
  (:require
   ;; Third-party libs
   [compojure.core :refer :all]
   [ring.swagger.ui :as ring-swagger-ui]

   ;; CMR libs
   [cmr.collection-renderer.api.routes :as collection-renderer-routes]
   [cmr.common.api.context :as context]
   [cmr.common-app.api-docs :as api-docs]))

(defn build-routes [system]
  (let [relative-root-url (get-in system [:public-conf :relative-root-url])]
    (routes
      (context relative-root-url []
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
