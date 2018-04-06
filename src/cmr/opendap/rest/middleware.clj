(ns cmr.opendap.rest.middleware
  "Custom ring middleware for CMR Graph."
  (:require
   [clojure.set :as set]
   [cmr.opendap.rest.response :as response]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn wrap-cors
  [handler]
  (fn [request]
    (response/cors request (handler request))))

(defn wrap-enforce-roles
  [handler]
  (fn [{:keys [::roles] :as request}]
    (println "Running perms middleware ...")
    (println "request:" request)
    (let [required (some-> request (ring/get-match) :data :roles)]
      (println "required:" required)
      (println "get-match:" (ring/get-match request))
      (println "data:" (:data (ring/get-match request)))
      (println "data keys:" (keys (:data (ring/get-match request))))
      (println "roles:" (:roles (:get (:data (ring/get-match request)))))
      (if (and (seq required) (not (set/subset? required roles)))
        {:status 403, :body "You do not have permissions to access that resource."}
        (handler request)))))
