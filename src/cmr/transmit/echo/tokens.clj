(ns cmr.transmit.echo.tokens
  "Contains functions for working with tokens using the echo-rest api."
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]))

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

(defn login-guest
  "Logs in as a guest and returns the token."
  [context]
  (login context "guest" "guest-password"))

(defn get-current-sids
  "Gets the 'security identifiers' for the user as string group guids and :registered and :guest"
  [context token]
  (let [[status sids body] (r/rest-get context (format "/tokens/%s/current_sids" token))]
    (case status
      200 (mapv c/echo-sid->cmr-sid sids)
      (r/unexpected-status-error! status body))))

