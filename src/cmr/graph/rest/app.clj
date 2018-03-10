(ns cmr.graph.rest.app
  (:require
   [cheshire.core :as json]
   [cmr.graph.health :as health]
   [reitit.ring :as ring]
   [ring.util.http-response :as response]
   [taoensso.timbre :as log]))

(defn json-response
  [data _request]
  (-> data
      json/generate-string
      response/ok
      (response/content-type "application/json")))

(defn health-response
  [component _request]
  (-> component
      health/components-ok?
      json/generate-string
      response/ok
      (response/content-type "application/json")))

(defn not-found
  [_request]
  (response/content-type
   (response/not-found "Not Found")
   "plain/text"))

(defn app
  [httpd-component]
  (ring/ring-handler
    (ring/router
      [["/health" {:get (partial health-response httpd-component)}]
       ["/ping" {:get (partial json-response {:result :pong})
                :port (partial json-response {:result :pong})}]])
    (constantly not-found)))
