(ns cmr.graph.rest.app
  (:require
   [cheshire.core :as json]
   [reitit.ring :as ring]))


(defn handler-200
  [data _request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string (merge {:result :ok} data))})

(defn handler-404
  [_request]
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "Not Found"})

(def app
  (ring/ring-handler
    (ring/router
      ["/ping" {:get (partial handler-200 {:result :pong})
                :port handler-200}])
    handler-404))
