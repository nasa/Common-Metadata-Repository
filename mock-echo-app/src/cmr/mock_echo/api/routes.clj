(ns cmr.mock-echo.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [cheshire.core :as json]
            [clojure.set :as set]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.services.errors :as svc-errors]
            [cmr.system-trace.http :as http-trace]
            [cmr.mock-echo.api.tokens :as token-api]
            [cmr.mock-echo.api.providers :as providers-api]
            [cmr.mock-echo.api.acls :as acls-api]
            [cmr.mock-echo.data.token-db :as token-db]
            [cmr.mock-echo.data.provider-db :as provider-db]
            [cmr.mock-echo.data.acl-db :as acl-db]))

(defn reset
  [context]
  (token-db/reset context)
  (provider-db/reset context)
  (acl-db/reset context))

(defn- build-routes [system]
  (routes

    (POST "/reset" {context :request-context}
      (reset context)
      {:status 204})

    ;; Add routes
    (token-api/build-routes system)
    (providers-api/build-routes system)
    (acls-api/build-routes system)

    (route/not-found "Not Found")))

(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      (errors/exception-handler (fn [_] "application/json"))
      handler/site
      ring-json/wrap-json-response))
