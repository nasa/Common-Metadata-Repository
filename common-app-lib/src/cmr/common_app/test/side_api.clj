(ns cmr.common-app.test.side-api
  "A namespace that creates a web server for controlling a system under test. It allows the system to be
  stopped for easy testing in CI."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.middleware.json :as ring-json]
            [ring.middleware.params :as params]
            [ring.middleware.nested-params :as nested-params]
            [ring.middleware.keyword-params :as keyword-params]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.config :refer [defconfig]]
            [cmr.common-app.api.routes :as common-routes]
            [cmr.common.api.web-server :as web]))

(defconfig side-api-port
  "Defines the port that the side API will use."
  {:default 2999
   :type Long})

(defn- build-routes
  "Builds the routes for the side API. Primarily uses the routes-fn to define routes."
  [routes-fn system]
  (routes
    (routes-fn system)
    (route/not-found "Not Found")))

(defn make-api-fn [routes-fn]
  "Returns a function that can be used to create the side routes API."
  (fn [system]
   (-> (build-routes routes-fn system)
       keyword-params/wrap-keyword-params
       nested-params/wrap-nested-params
       errors/exception-handler
       ring-json/wrap-json-response
       common-routes/pretty-print-response-handler
       params/wrap-params)))

(defn create-side-server
  "Creates a web server that will handle requests using the routes defined by the routes function.
   routes-fn should be a function of 1 argument that takes the system."
  [routes-fn]
  (web/create-web-server (side-api-port) (make-api-fn routes-fn) false false))


