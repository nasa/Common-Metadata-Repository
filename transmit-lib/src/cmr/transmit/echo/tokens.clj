(ns cmr.transmit.echo.tokens
  "Contains functions for working with tokens using the echo-rest api."
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.string :as string]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.log :refer [info]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.echo.rest :as r]))

(defn login
  "Logs into ECHO and returns the token"
  ([context username password]
   (login context username password "CMR Internal" "127.0.0.1"))
  ([context username password client-id user-ip-address]
   (let [token-info {:token {:username username
                             :password password
                             :client_id client-id
                             :user_ip_address user-ip-address}}
         [status parsed body] (r/rest-post context "/tokens" token-info)]
     (case status
       201 (get-in parsed [:token :id])
       504 (r/gateway-timeout-error!)

       ;; default
       (r/unexpected-status-error! status body)))))

(defn logout
  "Logs out of ECHO"
  [context token]
  (let [[status body] (r/rest-delete context (str "/tokens/" token))]
    (when-not (= 200 status)
      (r/unexpected-status-error! status body))))

(defn login-guest
  "Logs in as a guest and returns the token."
  [context]
  (login context "guest" "guest-password"))

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
           (format "Token %s does not exist" token))
         :else
         (r/unexpected-status-error! 500 (format "Unexpected error unsiging token locally. %s" error-data)))))))

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
         (:errors (json/decode body true)))
    404 (errors/throw-service-error
         :unauthorized
         (format "Token %s does not exist" token))

    ;; catalog-rest returns 401 when echo-rest returns 400 for expired token, we do the same in CMR
    400 (errors/throw-service-errors :unauthorized (:errors (json/decode body true)))

    ;; Gateway Timeout
    504 (errors/throw-service-errors :gateway-timeout ["A gateway timeout occurred, please try your request again later."])

    ;; default
    (r/unexpected-status-error! status body)))

(defn get-user-id
  "Get the user-id from ECHO for the given token"
  [context token]
  (if (transmit-config/echo-system-token? token)
    ;; Short circuit a lookup when we already know who this is.
    (transmit-config/echo-system-username)
    (if (and (common-util/is-jwt-token? token)
             (transmit-config/local-edl-verification))
      (verify-edl-token-locally token)
      (let [[status parsed body] (r/rest-post context "/tokens/get_token_info"
                                              {:headers {"Accept" mt/json
                                                         "Authorization" (transmit-config/echo-system-token)}
                                               :form-params {:id token}})]
        (info (format "get_token_info call on token [%s] (partially redacted) returned with status [%s]"
                      (common-util/scrub-token token) status))
        (handle-get-user-id token status parsed body)))))
