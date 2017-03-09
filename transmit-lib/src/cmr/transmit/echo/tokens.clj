(ns cmr.transmit.echo.tokens
  "Contains functions for working with tokens using the echo-rest api."
  (:require
   [cheshire.core :as json]
   [clojure.string :as s]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
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

    (let [[status parsed body] (r/rest-get context (format "/tokens/%s/token_info",token)
                                           {:headers {"Accept" mt/json
                                                      "Echo-Token" (transmit-config/echo-system-token)}})]
      (case (int status)
        200 (get-in parsed [:token_info :user_name])
        401 (errors/throw-service-error
             :unauthorized
             (format "Token %s does not exist" token))
        ;; catalog-rest returns 401 when echo-rest returns 400 for expired token, we do the same in CMR
        400 (errors/throw-service-errors :unauthorized  (:errors (json/decode body true)))
        (r/unexpected-status-error! status body)))))

(defn get-current-sids
  "Gets the 'security identifiers' for the user as string group guids and :registered and :guest"
  [context token]
  (let [[status sids body] (r/rest-get context (format "/tokens/%s/current_sids" token))]
    (case (int status)
      200 (mapv c/echo-sid->cmr-sid sids)
      401 (errors/throw-service-error
            :unauthorized
            (format "Token %s does not exist" token))
          
      ;; catalog-rest returns 401 when echo-rest returns 400 for expired token, we do the same in CMR
      400 (errors/throw-service-errors :unauthorized (:errors (json/decode body true)))

      ;; occasional 404's were being returned, causing an unexpected-status-error and triggering an alert
      404 (errors/throw-service-error :unauthorized (format "Token %s was not found" token))
      (r/unexpected-status-error! status body))))
