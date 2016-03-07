(ns cmr.transmit.echo.soap.authentication
  "Helper to perform Authentication tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))

(def security-token-info-tags [ :token :user-guid :act-as-user-guid :on-behalf-of-provider-guid
                                :created :expires :guest :revoked :client-id :user-name])

(defn- login-request
  "Returns a hiccup representation of the SOAP body for a Login request using the provided parameters."
  [user pass]
  ["ns2:Login"
    soap/soap-ns-map
    ["ns2:username" user]
    ["ns2:password" pass]
    ["ns2:clientInfo"
     ["ns3:ClientId" "curl"]
     ["ns3:UserIpAddress" "127.0.0.1"]]
    ["ns2:actAsUserName" ""]
    ["ns2:behalfOfProvider" ""]])

(defn- logout-request
  "Returns a hiccup representation of the SOAP body for a Logout request using the provided parameters."
  [token]
  ["ns2:Logout"
    soap/soap-ns-map
    ["ns2:token" token]])

(defn- get-security-token-informaton-request
  "Returns a hiccup representation of the SOAP body for a GetSecurityTokenInformation request using the provided parameters."
  [admin-token tokens]
  ["ns2:GetSecurityTokenInformation"
    soap/soap-ns-map
    ["ns2:token" admin-token]
    ["ns2:tokens" (soap/item-list tokens)]])

(defn login
  "Perform a login request against the SOAP API using the specified username and pass and return the generated token."
  [user pass]
  (let [[status body-xml] (soap/post-soap :authentication
                            (login-request user pass))]
      (xp/value-of body-xml "/Envelope/Body/LoginResponse/result")))

(defn logout
  "Perform a logout request against the SOAP API for the specified token.  Returns nil."
  [token]
  (let [[status body-xml] (soap/post-soap :authentication
                            (logout-request token))]
      (xp/value-of body-xml "/Envelope/Body/LogoutResponse/result")
      nil))

(defn get-security-token-informaton
  "Perform a security token information request against the SOAP API using the specified token."
  [admin-token tokens]
  (soap/item-map-list-from-soap-request :authentication :get-security-token-information
    (get-security-token-informaton-request admin-token tokens) security-token-info-tags))
