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
    {"xmlns:ns2" "http://echo.nasa.gov/echo/v10"
     "xmlns:ns3" "http://echo.nasa.gov/echo/v10/types"
     "xmlns:ns4" "http://echo.nasa.gov/ingest/v10"}
    ["ns2:username" user]
    ["ns2:password" pass]
    ["ns2:clientInfo"
     ["ns3:ClientId" "curl"]
     ["ns3:UserIpAddress" "127.0.0.1"]]
    ["ns2:actAsUserName" ""]
    ["ns2:behalfOfProvider" ""]])

(defn login
  "Perform a login request against the SOAP API and return the generated token."
  [user pass]
  (let [[status body-xml] (soap/post-soap :authentication
                            (login-request user pass))]
      (xp/value-of body-xml "/Envelope/Body/LoginResponse/result")))
