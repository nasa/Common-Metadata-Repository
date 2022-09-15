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

;; Note: Similar code exists at gov.nasa.echo.kernel.service.authentication
(def URS_TOKEN_MAX_LENGTH 100)
(def BEARER "Bearer ")

(defn- is-legacy-token?
  "There are two uses cases captured by this test, the Legacy token and the
   new style legacy token made to behave like a legacy token but are prefixed
   with EDL- to aid in indentification. This function will not match very short
   JWT tokens.
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [token]
  (or (<= (count token) URS_TOKEN_MAX_LENGTH)
      (string/starts-with? token "EDL-")
      (string/starts-with? token (str BEARER "EDL-"))))

(defn is-launchpad-token?
  "Returns true if the given token is a launchpad token.
   If the token is not a Legacy (ECHO), Heritage (EDL+), or JWT (newest) token,
   then it must be a Launchpad token.
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [token]
  ;; note: ordered from least expensive to most
  (not (or (is-legacy-token? token)
           (common-util/is-jwt-token? token))))

(defn get-token-type
  "Returns the type of a given token"
  [token]
  (when (string? token)
    (cond
      (= (transmit-config/echo-system-token) token) "System"
      (re-seq #"[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}" token) "Echo-Token"
      (is-legacy-token? token) "Legacy-EDL"
      (common-util/is-jwt-token? token) "JWT"
      :else "Launchpad")))

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
