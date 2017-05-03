(ns cmr.access-control.routes
  "This namespace is the one responsible for combining the routes intended for
  use by libraries and applications (API routes) and the routes intended for
  human, browser, or web crawler consumption (site routes). It also provides
  a place for adding routes that apply to both (e.g., robots.txt, should we
  ever need that for access-control)."
  (:require
   [cmr.access-control.api.routes :as api-routes]
   [cmr.access-control.site.routes :as site-routes]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.site.pages :as common-pages]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as api-errors]
   [compojure.core :refer [routes]]
   [ring.middleware.keyword-params :as keyword-params]
   [ring.middleware.nested-params :as nested-params]
   [ring.middleware.params :as params]))

(defn build-routes [system]
  (routes
    (api-routes/build-routes system)
    (site-routes/build-routes system)
    (common-pages/not-found)))

(defn handlers [system]
  (-> (build-routes system)
      acl/add-authentication-handler
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      keyword-params/wrap-keyword-params
      nested-params/wrap-nested-params
      api-errors/invalid-url-encoding-handler
      api-errors/exception-handler
      common-routes/pretty-print-response-handler
      params/wrap-params))
