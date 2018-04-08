(ns cmr.opendap.rest.middleware
  "Custom ring middleware for CMR Graph."
  (:require
   [cmr.opendap.auth.permissions :as permissions]
   [cmr.opendap.auth.token :as token]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.response :as response]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn wrap-cors
  [handler]
  (fn [request]
    (response/cors request (handler request))))

(defn wrap-enforce-roles
  [handler system]
  (fn [request]
    (log/debug "Running perms middleware ...")
    (let [roles (get-in (ring/get-match request) [:data :get :roles])
          cmr-base-url (config/cmr-base-url system)
          user-token (token/extract request)
          user-id (token/->cached-user system cmr-base-url user-token)]
      (log/trace "cmr-base-url:" cmr-base-url)
      (log/trace "user-token:" user-token)
      (log/trace "user-id:" user-id)
      (log/debug "roles:" roles)
      ;; XXX For now, there is only the admin role in the CMR, so we'll just
      ;;     keep this specific to that, for now. Later, if more roles are
      ;;     used, we'll want to make this more generic ...
      (if roles
        (if (permissions/admin? system roles cmr-base-url user-token user-id)
          (handler request)
          (response/not-allowed
            "You do not have permissions to access that resource."))
        (handler request)))))

(defn reitit-enforce-roles
  [system]
  {:data
    {:middleware [#(wrap-enforce-roles % system)]}})
