(ns cmr.opendap.auth.token
  (:require
   [cmr.authz.token :as token]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [taoensso.timbre :as log]))

(defn ->cached-user
  "Look up the user for a token in the cache; if there is a miss, make the
  actual call for the lookup."
  [system token]
  (try
    (caching/lookup
     system
     (token/user-id-key token)
     #(token/->user (config/get-echo-rest-url system) token))
    (catch Exception e
      (ex-data e))))
