(ns cmr.transmit.echo.soap.authentication
  "Helper to perform Authentication tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))

(def "Keys within a security token info map"
  security-token-info-tags [ :token :user-guid :act-as-user-guid :on-behalf-of-provider-guid
                                :created :expires :guest :revoked :client-id :user-name])

(defn login
  "Perform a login request against the SOAP API using the specified username and pass and return the generated token."
  [user pass]
  (let [body ["ns2:Login"
                soap/soap-ns-map
                ["ns2:username" user]
                ["ns2:password" pass]
                ["ns2:clientInfo"
                 ["ns3:ClientId" "curl"]
                 ["ns3:UserIpAddress" "127.0.0.1"]]
                ["ns2:actAsUserName" ""]
                ["ns2:behalfOfProvider" ""]]]
      (-> (soap/post-soap :authentication body)
          (soap/extract-string :login))))

(defn logout
  "Perform a logout request against the SOAP API for the specified token.  Returns nil."
  [token]
  (let [body ["ns2:Logout"
                soap/soap-ns-map
                ["ns2:token" token]]]
    (soap/post-soap :authentication body)
    nil))

(defn get-security-token-informaton
  "Perform a security token information request against the SOAP API using the specified token."
  [admin-token tokens]
  (let [body ["ns2:GetSecurityTokenInformation"
                soap/soap-ns-map
                ["ns2:token" admin-token]
                ["ns2:tokens" (soap/item-list tokens)]]]
      (-> (soap/post-soap :authentication body)
          (soap/extract-item-map-list :get-security-token-information security-token-info-tags))))
