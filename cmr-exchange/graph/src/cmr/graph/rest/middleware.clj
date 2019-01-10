(ns cmr.graph.rest.middleware
  "Custom ring middleware for CMR Graph."
  (:require
   [cmr.graph.rest.response :as response]
   [taoensso.timbre :as log]))

(defn wrap-cors
  [handler]
  (fn [request]
    (response/cors request (handler request))))
