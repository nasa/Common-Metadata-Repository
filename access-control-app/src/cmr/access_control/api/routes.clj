(ns cmr.access-control.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as api-errors]
            [cmr.common.api.context :as context]
            [cmr.acl.core :as acl]
            [cmr.common-app.api.routes :as common-routes]
            [cmr.common-app.api-docs :as api-docs]))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []

      ;; Add routes for API documentation
      (api-docs/docs-routes (get-in system [:access-control-public-conf :protocol])
                            (get-in system [:access-control-public-conf :relative-root-url])
                            "public/access_control_index.html")

      (context "/foo" []
        (GET "/" {params :params headers :headers context :request-context}
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body "foo"})))
    (route/not-found "Not Found")))

(defn make-api [system]
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



