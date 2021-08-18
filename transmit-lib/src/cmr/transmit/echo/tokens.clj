(ns cmr.transmit.echo.tokens
  "Contains functions for working with tokens using the echo-rest api."
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.string :as s]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as common-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.echo.conversion :as c]
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
     (if (= 201 status)
       (get-in parsed [:token :id])
       (r/unexpected-status-error! status body)))))

(defn logout
  "Logs into ECHO and returns the token"
  [context token]
  (let [[status body] (r/rest-delete context (str "/tokens/" token))]
    (when-not (= 200 status)
      (r/unexpected-status-error! status body))))

(defn login-guest
  "Logs in as a guest and returns the token."
  [context]
  (login context "guest" "guest-password"))

(defn get-user-id
  "Get the user-id from ECHO for the given token"
  [context token]
  (if (transmit-config/echo-system-token? token)
    ;; Short circuit a lookup when we already know who this is.
    (transmit-config/echo-system-username)

    (let [[status parsed body] (r/rest-post context "/tokens/get_token_info"
                                            {:headers {"Accept" mt/json
                                                       "Authorization" (transmit-config/echo-system-token)}
                                             :form-params {:id token}})]
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
        400 (errors/throw-service-errors :unauthorized  (:errors (json/decode body true)))
        (r/unexpected-status-error! status body)))))
