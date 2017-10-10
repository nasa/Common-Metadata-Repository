(ns cmr.virtual-product.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common.api.errors :as errors]
   [cmr.common.api.context :as context]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.virtual-product.services.health-service :as hs]
   [cmr.virtual-product.services.translation-service :as ts]
   [compojure.core :refer :all]
   [compojure.handler :as handler]
   [compojure.route :as route]
   [ring.middleware.json :as ring-json]))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      ;; for NGAP deployment health check
      (GET "/" {} {:status 200})
      (context "/translate-granule-entries" []
        (POST "/" {:keys [body content-type headers request-context]}
          (if (= (mt/mime-type->format content-type) :json)
            {:status 200
             :body (ts/translate request-context (slurp body))}
            {:status 415
             :body (str "Unsupported content type [" content-type "]")})))

      (common-health/health-api-routes hs/health))
    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      common-routes/add-request-id-response-handler
      (context/build-request-context-handler system)
      handler/site
      common-routes/pretty-print-response-handler
      ring-json/wrap-json-response))
