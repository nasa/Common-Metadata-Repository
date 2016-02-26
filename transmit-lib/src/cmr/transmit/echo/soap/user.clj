(ns cmr.transmit.echo.soap.user
  "Helper to perform User tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))

(defn create-user-request
  "Returns a hiccup representation of the SOAP body for a CreateUser request using the provided parameters."
  [param-map]
  (let [{:keys [token password guid user-domain user-region primary-study-area user-type username title
                first-name middle-initial last-name param-map email opt-in organization-name addresses
                phones roles creation-date]} param-map]
    ["ns2:CreateUser"
      {"xmlns:ns2" "http://echo.nasa.gov/echo/v10"
       "xmlns:ns3" "http://echo.nasa.gov/echo/v10/types"
       "xmlns:ns4" "http://echo.nasa.gov/ingest/v10"}
      ["ns2:token" token]
      ["ns2:password" password]
      ["ns2:newUser"
        ;; NOTE the when forms arent really necessary as empty elements will be ommitted when we convert
        ;; to XML anyway, but this makes it easier to see which elements are required and which arent.
        ;;  Ideally we will implement a better approach.
        (when guid ["ns3:UserDomain" guid])
        ["ns3:UserDomain" user-domain]
        ["ns3:UserRegion" user-region]
        (when primary-study-area ["ns3:PrimaryStudyArea" primary-study-area])
        (when user-type ["ns3:UserType" user-type])
        ["ns3:Username" username]
        (when title ["ns3:Title" title])
        ["ns3:FirstName" first-name]
        (when middle-initial ["ns3:MiddleInitial" middle-initial])
        ["ns3:LastName" last-name]
        ["ns3:Email" email]
        ["ns3:OptIn" opt-in]
        (when organization-name ["ns3:OrganizationName" organization-name])
        ;; For now, addresses, phones, and roles need to be passed in already in hiccup format
        ["ns3:Addresses"
          (for [address addresses]["ns3:Item" address])]
        (when phones ["ns3:Phones"
                      (for [phone phones] ["ns3:Item" phone])])
        (when roles ["ns3:Roles"
                      (for [role roles] ["ns3:Item" role])])
        (when creation-date ["ns3:CreationDate" creation-date])]]))

(defn create-user
  "Perform a CreateUser request against the SOAP API."
  [param-map]
  (let [[status body-xml] (soap/post-soap :user
                            (create-user-request param-map))]
      (xp/value-of body-xml "/Envelope/Body/CreateUserResponse/result")))
