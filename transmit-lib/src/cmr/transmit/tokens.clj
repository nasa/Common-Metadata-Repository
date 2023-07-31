(ns cmr.transmit.tokens
  "Contains functions for working with tokens"
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.launchpad-user-cache :as launchpad-user-cache]))

(defn verify-edl-token-locally
  "Uses the EDL public key to verify jwt tokens locally."
  [token]
  (try
    (let [public-key (buddy-keys/jwk->public-key (json/parse-string (transmit-config/edl-public-key) true))
          bearer-stripped-token (string/replace token #"Bearer\W+" "")
          decrypted-token (jwt/unsign bearer-stripped-token public-key {:alg :rs256})]
      (:uid decrypted-token))
    (catch clojure.lang.ExceptionInfo ex
      (let [error-data (ex-data ex)]
        (cond
          (= :exp (:cause error-data))
          (errors/throw-service-error
           :unauthorized
           (format "Token [%s] has expired. Note the token value has been partially redacted."
                   (common-util/scrub-token token)))
          (= :signature (:cause error-data))
          (errors/throw-service-error
           :unauthorized
           (format "Token %s does not exist" (common-util/scrub-token token)))
          :else
          (r/unexpected-status-error! 500 (format "Unexpected error unsiging token locally. %s" error-data)))))))

(defn get-user-id
  "Get the user-id from EDL or Launchpad for the given token"
  [context token]
  (if (transmit-config/echo-system-token? token)
    ;; Short circuit a lookup when we already know who this is.
    (transmit-config/echo-system-username)
    (if (and (common-util/is-jwt-token? token)
             (transmit-config/local-edl-verification))
      (verify-edl-token-locally token)
      (if (common-util/is-launchpad-token? token)
        (:uid (launchpad-user-cache/get-launchpad-user context token))
        (errors/throw-service-errors
         :unauthorized
         (format "Token %s is invalid" (common-util/scrub-token token)))))))