(ns cmr.mock-echo.client.mock-echo-client
  "Contains functions for communicating with the mock echo api that aren't normal echo-rest
  operations"
  (:require [cmr.transmit.echo.rest :as r]
            [cmr.transmit.echo.conversion :as c]))

(defn reset
  "Clears out all data in mock echo"
  [context]
  (r/rest-post context "/reset" nil))

(defn create-providers
  "Creates the providers in mock echo given a provider-guid-to-id-map"
  [context provider-guid-to-id-map]
  (let [providers (for [[guid provider-id] provider-guid-to-id-map]
                    {:provider {:id guid
                                :provider_id provider-id}})
        [status] (r/rest-post context "/providers" providers)]
    (when-not (= status 201)
      (r/unexpected-status-error! status nil))))

(defn create-acl
  "Creates an ACL in mock echo. Takes cmr style acls. Returns the acl with the guid"
  [context acl]
  (let [[status acl body] (r/rest-post context "/acls" (c/cmr-acl->echo-acl acl))]
    (if (= status 201)
      acl
      (r/unexpected-status-error! status body))))

(defn delete-acl
  [context guid]
  (let [[status body] (r/rest-delete context (str "/acls/" guid))]
    (when-not (= status 200)
      (r/unexpected-status-error! status body))))

(defn login-with-group-access
  "Logs into mock echo and returns the token. The group guids passed in will be returned as a part
  of the current_sids of the user"
  [context username password group-guids]
  (let [token-info {:token {:username username
                            :password password
                            :client_id "CMR Internal"
                            :user_ip_address "127.0.0.1"
                            :group_guids group-guids}}
        [status parsed body] (r/rest-post context "/tokens" token-info)]
    (if (= 201 status)
      (get-in parsed [:token :id])
      (r/unexpected-status-error! status body))))
