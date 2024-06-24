(ns cmr.transmit.tokens
  "Contains functions for working with tokens"
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clj-http.client :as client]
   [clojure.string :as string]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.log :as log :refer (info warn)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.connection :as conn]
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

(defn unexpected-status-error!
  [status body]
  (errors/internal-error!
   ; Don't print potentially sensitive information
   (if (re-matches #".*token .* does not exist.*" body)
     (format "Unexpected status %d from response. body: %s" status "Token does not exist")
     (format "Unexpected status %d from response. body: %s" status (pr-str body)))))

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

    ;; legacy services endpoint is shutdown, so we will recieve a 301 when querying token info.
    301 (errors/throw-service-error
         :unauthorized
         (format "Token %s does not exist" (common-util/scrub-token token)))

    ;; catalog-rest returns 401 when echo-rest returns 400 for expired token, we do the same in CMR
    400 (errors/throw-service-errors :unauthorized (:errors (json/decode body true)))

    ;; Service Temporarily Unavailable
    503 (errors/throw-service-errors :service-unavailable ["Service temporarily unavailable, please try your request again later."])

    ;; Gateway Timeout
    504 (errors/throw-service-errors :gateway-timeout ["A gateway timeout occurred, please try your request again later."])

    ;; default
    (unexpected-status-error! status body)))

(defn request-options
  [conn]
  (merge
   (transmit-config/conn-params conn)
   {:accept :json
    :throw-exceptions false
    :headers {"Authorization" (transmit-config/echo-system-token)}
     ;; Overrides the socket timeout from conn-params
    :socket-timeout (transmit-config/echo-http-socket-timeout)}))

(defn post-options
  [conn body-obj]
  (merge (request-options conn)
         {:content-type :json
          :body (json/encode body-obj)}))

(defn rest-post
  "Makes a post request to echo-rest. Returns a tuple of status, the parsed body, and the body."
  ([context url-path body-obj]
   (rest-post context url-path body-obj {}))
  ([context url-path body-obj options]
   (warn (format "Using legacy API call to POST %s!!!!!!!!!}" url-path))
   (let [conn (transmit-config/context->app-connection context :echo-rest)
         url (format "%s%s" (conn/root-url conn) url-path)
         params (if (some? (:form-params body-obj))
                  (merge (request-options conn) body-obj)
                  (merge (post-options conn body-obj) options))
         _ (warn (format "Using legacy API call to POST %s with params: %s" url params))
         response (client/post url params)
         {:keys [status body headers]} response
         parsed (when (.startsWith ^String (get headers "Content-Type" "") "application/json")
                  (json/decode body true))]
     [status parsed body])))

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
        ;; Legacy services has been shut down, we still use a mock for tests, we will leave this here and handle the 301.
        (let [[status parsed body] (rest-post context "/tokens/get_token_info"
                                                    {:headers {"Accept" mt/json
                                                               "Authorization" (transmit-config/echo-system-token)}
                                                     :form-params {:id token}})]
             (info (format "get_token_info call on token [%s] (partially redacted) returned with status [%s]"
                           (common-util/scrub-token token) status))
             (handle-get-user-id token status parsed body))))))
