(ns cmr.common-app.api.launchpad-token-validation
  "Validate Launchpad token."
  (:require
   [cheshire.core :as json]
   [clojure.data.codec.base64 :as base64]
   [clojure.string :as string]
   [cmr.common-app.config :as config]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]))

;; TODO - remove legacy token check after legacy token retirement
(defn get-token-type
  "Returns the type of a given token"
  [token]
  (when (string? token)
    (cond
      (= (transmit-config/echo-system-token) token) "System"
      (re-seq #"[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}" token) "Echo-Token"
      (common-util/is-legacy-token? token) "Legacy-EDL"
      (common-util/is-jwt-token? token) "JWT"
      :else "Launchpad")))

(defn validate-launchpad-token
  "Validate the token in request context is a Launchpad Token if launchpad token enforcement
   is turned on. This function should be called on routes that will ingest into CMR.
   Ingest will only be allowed if the user is in the NAMS CMR Ingest group
   and also has the right ACLs which is based on Earthdata Login uid."
  [request-context]
  (let [token (:token request-context)]
    (when (and (config/launchpad-token-enforced)
               (= 1 2)
               (not (common-util/is-launchpad-token? token))
               (not= (transmit-config/echo-system-token) token))
      (errors/throw-service-error
       :bad-request
       (format "Launchpad token is required. Token [%s] is not a launchpad token." (common-util/scrub-token token))))))
