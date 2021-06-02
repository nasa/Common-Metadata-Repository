(ns cmr.mock-echo.api.routes
  "Defines the HTTP URL routes for the application."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.common.api.context :as context]
   [cmr.common.api.errors :as errors]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as svc-errors]
   [cmr.mock-echo.api.acls :as acls-api]
   [cmr.mock-echo.api.providers :as providers-api]
   [cmr.mock-echo.api.tokens :as token-api]
   [cmr.mock-echo.api.urs :as urs-api]
   [cmr.mock-echo.data.acl-db :as acl-db]
   [cmr.mock-echo.data.provider-db :as provider-db]
   [cmr.mock-echo.data.token-db :as token-db]
   [cmr.mock-echo.data.urs-db :as urs-db]
   [compojure.core :refer :all]
   [compojure.handler :as handler]
   [compojure.route :as route]
   [ring.middleware.json :as ring-json]))

(defn reset
  [context]
  (token-db/reset context)
  (provider-db/reset context)
  (acl-db/reset context)
  (urs-db/reset context))

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (POST "/reset" {context :request-context}
        (reset context)
        {:status 204})

      ;; Add routes
      (token-api/build-routes system)
      (providers-api/build-routes system)
      (acls-api/build-routes system)
      (urs-api/build-routes system)

      (route/not-found "Not Found"))))

(defn make-api [system]
  (-> (build-routes system)
      (context/build-request-context-handler system)
      errors/invalid-url-encoding-handler
      errors/exception-handler
      handler/site
      ring-json/wrap-json-response))
