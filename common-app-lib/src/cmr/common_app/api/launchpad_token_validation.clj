(ns cmr.common-app.api.launchpad-token-validation
  "Validate Launchpad token."
  (:require
   [cheshire.core :as json]
   [clojure.data.codec.base64 :as base64]
   [clojure.string :as string]
   [cmr.common-app.config :as config]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.config :as transmit-config]))

;; Note: Similar code exists at gov.nasa.echo.kernel.service.authentication
(def URS_TOKEN_MAX_LENGTH 100)

(defn is-heritage-token?
  "Heritage token, meaning not the original style legacy token, but a modern
   token made to be identifiable but also comply with the legacy standard. These
   tokens are prefixed with 'EDL+'.
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [token]
  (string/starts-with? token "EDL+"))

(defn is-legacy-token?
  "A check of last resort, only used if all other checks have not passed.
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [token]
  (and (not (is-heritage-token? token))
       (<= (count token) URS_TOKEN_MAX_LENGTH)))

(defn is-jwt-token?
  "Check if a token matches the JWT pattern (Base64.Base64.Base64) and if it
   does, try to look inside the header section and verify that the token is JWT
   and it came from EarthDataLogin (EDL).
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [token]
  (if (some? (re-find #"[A-Za-z0-9+/=_-]+\.[A-Za-z0-9+/=_-]+\.[A-Za-z0-9+/=_-]+" token))
    (let [token-parts (string/split token #"\.")
          token-header (first token-parts)
          ;token-payload (second token-parts) ;;future work - look at experation date
          ;token-signiture (last token-parts) ;;future work - verify signature
          header-raw (String. (.decode (java.util.Base64/getDecoder) token-header))]
      ;; don't parse the data unless it is really needed to prevent unnecessary
      ;; processing. Check first to see if the data looks like JSON
      (if (and (string/starts-with? header-raw "{")
               (string/ends-with? header-raw "}"))
        (try
          (if-let [header-data (json/parse-string header-raw true)]
            (and (= "JWT" (:typ header-data))
                 (= "Earthdata Login" (:origin header-data)))
            false)
          (catch com.fasterxml.jackson.core.JsonParseException e false))
        false))
    false))

(defn is-launchpad-token?
  "Returns true if the given token is a launchpad token.
   If the token is not a Legacy (ECHO), Heritage (EDL+), or JWT (newest) token,
   then it must be a Launchpad token.
   Note: Similar code exists at gov.nasa.echo.kernel.service.authentication."
  [token]
  ;; note: ordered from least expensive to most
  (not (or (is-heritage-token? token)
           (is-legacy-token? token)
           (is-jwt-token? token))))

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
