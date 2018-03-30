(ns cmr.opendap.rest.middleware
  "Custom ring middleware for CMR Graph."
  (:require
   [cmr.opendap.rest.response :as response]
   [taoensso.timbre :as log]))

(defn wrap-cors
  [handler]
  (fn [request]
    (response/cors request (handler request))))
