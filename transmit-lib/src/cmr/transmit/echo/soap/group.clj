(ns cmr.transmit.echo.soap.group
  "Helper to perform Group tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))

(defn get-groups-request
  "Returns a hiccup representation of the SOAP body for a GetGroups2 request using the provided itoken."
  [param-map]
  (let [{:keys [token group-guids]} param-map]
    ["ns2:GetGroups2"
      soap/ns-map
      ["ns2:token" token]
      ["ns2:groupGuids" (soap/item-list group-guids)]]))

(defn get-group-names-by-member2
  "Returns a hiccup representation of the SOAP body for a GetGroupNamesByMember2 request using the provided itoken."
  [param-map]
  (let [{:keys [token member-guid]} param-map]
    ["ns2:GetGroupNamesByMember2"
      soap/ns-map
      ["ns2:token" token]
      ["ns2:memberGuid" member-guid]]))

(defn create-group-request
  "Returns a hiccup representation of the SOAP body for a GetGroups2 request using the provided itoken."
  [param-map]
  (let [{:keys [token name description member-guids managing-group guid provider-guid]} param-map]
    ["ns2:CreateGroup"
      soap/ns-map
      ["ns2:token" token]
      ["ns2:newGroup"
        (when guid ["ns3:Guid" guid])
        ["ns3:Name" name]
        ["ns3:Description" description]
        ["ns3:MemberGuids" (soap/item-list member-guids)]
        (when provider-guid ["ns3:OwnerProviderGuid" provider-guid])]
      ["ns2:managingGroupGuid" managing-group]]))

(defn create-group
  "Perform a CreateGroups request against the SOAP API."
  ;; param-map must contain : token name description member-guids managing-group
  ;;    and optionally: guid provider-guid
  [param-map]
  (let [[status body-xml] (soap/post-soap :group2_management
                            (create-group-request param-map))]
      (xp/value-of body-xml "/Envelope/Body/LoginResponse/result")))

(defn get-groups
  "Perform a GetGroups2 request against the SOAP API."
  [param-map]
  (let [[status body-xml] (soap/post-soap :group2_management
                            (get-groups-request param-map))]
      (xp/value-of body-xml "/Envelope/Body/GetGroups2Response/result")))

(defn get-group-names-by-member
  "Perform a GetGroupNamesByMember2 request against the SOAP API."
  [param-map]
  (let [[status body-xml] (soap/post-soap :group2_management
                            (get-group-names-by-member2 param-map))]
      (-> body-xml
          (xpath/create-xpath-context-for-xml)
          (xpath/evaluate (xpath/parse-xpath
                            "/Envelope/Body/GetGroupNamesByMemberResponse2/result/Item"))
          (:context))
      (xp/value-of body-xml "/Envelope/Body/GetGroups2Response/result")))
