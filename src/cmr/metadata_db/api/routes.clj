(ns cmr.metadata-db.api.routes
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
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.api.services :as services]))

(defn- build-routes [system]
  (routes
    (context "/foo" []
             (GET "/" []
                  {:status 200
                   :headers {"Content-Type" "text/plain"}
                   :body "foo"}))
    (context "/concepts" []
             (POST "/" params
                   (services/save-concept system (:body params))))
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



