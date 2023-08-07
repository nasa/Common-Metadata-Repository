(ns cmr.transmit.tokens
  "Contains functions for working with tokens"
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.string :as string]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as common-util]
   [cmr.mock-echo.client.echo-util :as echo-functionality]
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
          (errors/internal-error! (format "Unexpected error unsiging token locally. %s" error-data)))))))

(defn handle-get-user-id
  [token status parsed body]
  (case (int status)
    200 (let [expires (some-> (get-in parsed [:token_info :expires])
                              date-time-parser/parse-datetime)]
          (if (or (nil? expires)
                  (t/after? expires (tk/now)))
            (get-in parsed [:token_info :user_name])
            (errors/throw-service-error
             :unauthorized
             (format "Token [%s] has expired. Note the token value has been partially redacted."
                     (common-util/scrub-token token)))))
    401 (errors/throw-service-errors
         :unauthorized
         (let [errs (:errors (json/decode body true))]
           (if (string/includes? (first errs) "Caught exception")
             [(format "Token %s is invalid" (common-util/scrub-token token))]
             errs)))

    404 (errors/throw-service-error
         :unauthorized
         (format "Token %s does not exist" (common-util/scrub-token token)))

    ;; catalog-rest returns 401 when echo-rest returns 400 for expired token, we do the same in CMR
    400 (errors/throw-service-errors :unauthorized (:errors (json/decode body true)))

    ;; Service Temporarily Unavailable
    503 (errors/throw-service-errors :service-unavailable ["Service temporarily unavailable, please try your request again later."])

    ;; Gateway Timeout
    504 (errors/throw-service-errors :gateway-timeout ["A gateway timeout occurred, please try your request again later."])

    ;; default
    (echo-functionality/unexpected-status-error! status body)))

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
        (let [[status parsed body] (echo-functionality/rest-post context "/tokens/get_token_info"
                                                    {:headers {"Accept" mt/json
                                                               "Authorization" (transmit-config/echo-system-token)}
                                                     :form-params {:id token}})]
              (info (format "get_token_info call on token [%s] (partially redacted) returned with status [%s]"
                            (common-util/scrub-token token) status))
              (handle-get-user-id token status parsed body))))))