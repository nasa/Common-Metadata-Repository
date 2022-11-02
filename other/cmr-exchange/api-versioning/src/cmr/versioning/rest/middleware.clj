(ns cmr.versioning.rest.middleware
  "Custom reitit/ring middleware for CMR services that want to offer versioned APIs."
  (:require
   [clojusc.twig :refer [pprint]]
   [cmr.http.kit.response :as response]
   [cmr.versioning.rest.request :as request]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn wrap-no-auth
  [handler _system]
  (fn [req]
    (log/debug "Running without auth middleware ...")
    (handler req)))

(defn wrap-reitit-no-auth
  [system]
  {:data
    {:middleware [#(wrap-no-auth % system)]}})

(defn wrap-reitit-api-version-dispatch
  ""
  ([site-routes route-data system]
    (wrap-reitit-api-version-dispatch
      site-routes
      route-data
      system
      {:auth-wrapper wrap-reitit-no-auth}))
  ([site-routes
   {:keys [main-api-routes-fn plugins-api-routes-fns] :as _route-data}
   system
   {:keys [auth-wrapper] :as _opts}]
    (fn [req]
      (let [api-version (request/accept-api-version system req)
            plugins-api-routes (vec (mapcat #(% system api-version) plugins-api-routes-fns))
            api-routes (vec (main-api-routes-fn system api-version))
            routes (concat (vec site-routes) plugins-api-routes api-routes)
            handler (->> system
                         (auth-wrapper)
                         (ring/router routes)
                         (ring/ring-handler))]
        (log/debug "API version:" api-version)
        (log/debug "Made routes:" (pprint routes))
        (response/version-media-type
          (handler req)
          (request/accept-media-type-format system req))))))
