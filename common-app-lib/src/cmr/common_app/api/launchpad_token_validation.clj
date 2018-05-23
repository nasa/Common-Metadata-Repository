(ns cmr.common-app.api.launchpad-token-validation
  "Validate Launchpad token."
  (:require
   [cmr.common-app.config :as config]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.config :as transmit-config]))

(def URS_TOKEN_MAX_LENGTH 100)

(defn is-launchpad-token?
  "Returns true if the given token is a launchpad token.
   Currently we only check the length of the token to decide."
  [token]
  (> (count token) URS_TOKEN_MAX_LENGTH))

(defn validate-launchpad-token
  "Validate the token in request context is a Launchpad Token if launchpad token enforcement
   is turned on. This function should be called on routes that will ingest into CMR.
   Ingest will only be allowed if the user is in the NAMS CMR Ingest group
   (done in legacy services) and also has the right ACLs which is based on Earthdata Login uid."
  [request-context]
  (let [token (:token request-context)]
    (when (and (config/launchpad-token-enforced)
               (not (is-launchpad-token? token))
               (not= (transmit-config/echo-system-token) token))
      (errors/throw-service-error
       :bad-request
       (format "Launchpad token is required. Token [%s] is not a launchpad token." token)))))
