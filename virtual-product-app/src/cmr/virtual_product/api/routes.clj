(ns cmr.virtual-product.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [cmr.common-app.api.routes :as common-routes]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.api.context :as context]
            [cmr.common.mime-types :as mt]
            [cmr.virtual-product.services.translation-service :as ts]
            [cmr.virtual-product.services.health-service :as hs]))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/translate-granule-entries" []
        (POST "/" {:keys [body content-type headers request-context]}
          (if (= (mt/mime-type->format content-type) :json)
            {:status 200
             :body (ts/translate request-context (slurp body))}
            {:status 415
             :body (str "Unsupported content type [" content-type "]")})))

      (common-routes/health-api-routes hs/health))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (context/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      ring-json/wrap-json-response))

