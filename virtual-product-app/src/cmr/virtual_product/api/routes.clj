(ns cmr.virtual-product.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [cmr.common-app.api.routes :as common-routes]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.virtual-product.services.health-service :as hs]))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/foo" []
        (GET "/" {params :params headers :headers context :request-context}
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body "foo"}))

      (common-routes/health-api-routes hs/health))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



