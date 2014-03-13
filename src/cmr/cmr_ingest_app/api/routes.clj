(ns cmr.cmr-ingest-app.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as string]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre
             :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))

(defn- build-routes [system]
  (routes
    (context "/foo" []
      (GET "/" []
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body "foo"}))
    (route/not-found "Not Found")))

(defn- exception-handler
  [f]
  (fn [request]
    (try (f request)
      (catch clojure.lang.ExceptionInfo e
        {:status (-> e
                     ex-data
                     :type
                     errors/type->http-status-code)
         :body (.getMessage e)})
      (catch Exception e
        (error e)
        {:status 500 :body "Bad Stuff!!!"}))))

(defn make-api [system]
  (-> (build-routes system)
      exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))



