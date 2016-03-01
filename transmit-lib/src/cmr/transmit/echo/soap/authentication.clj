(ns cmr.transmit.echo.soap.authentication
  "Helper to perform Authentication tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))


(defn login-request
  "Returns a hiccup representation of the SOAP body for a Login request using the provided user and pass."
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

(defn logout-request
  "Returns a hiccup representation of the SOAP body for a Logout request using the provided user and pass."
  [token]
  ["ns2:Logout"
    soap/soap-ns-map
    ["ns2:token" token]])

(defn get-security-token-informaton-request
  "Returns a hiccup representation of the SOAP body for a GetSecurityTokenInformation request using the provided user and pass."
  [admin-token tokens]
  ["ns2:GetSecurityTokenInformation"
    soap/soap-ns-map
    ["ns2:token" admin-token]
    ["ns2:tokens" (soap/item-list tokens)]])

(defn login
  "Perform a login request against the SOAP API and return the generated token."
  [user pass]
  (let [[status body-xml] (soap/post-soap :authentication
                            (login-request user pass))]
      (xp/value-of body-xml "/Envelope/Body/LoginResponse/result")))

(defn logout
  "Perform a login request against the SOAP API and return the generated token."
  [token]
  (let [[status body-xml] (soap/post-soap :authentication
                            (logout-request token))]
      (xp/value-of body-xml "/Envelope/Body/LogoutResponse/result")))

(defn get-security-token-informaton
  "Perform a security token information request against the SOAP API and return the generated token."
  [admin-token tokens]
  (let [[status body-xml] (soap/post-soap :authentication
                            (get-security-token-informaton-request admin-token tokens))]
      (-> body-xml
          (xpath/create-xpath-context-for-xml)
          (xpath/evaluate (xpath/parse-xpath
                            "/Envelope/Body/GetSecurityTokenInformationResponse/result/Item"))
          (:context))))
