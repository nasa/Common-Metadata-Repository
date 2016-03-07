(ns cmr.transmit.echo.soap.group
  "Helper to perform Group tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.transmit.echo.soap.user :as user]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]))

;; Keys within a group map
(def group-keys [:guid :name :description :member-guids :owner-provider-id :managing-group-guid])

;; A minimally valid group map
(def minimal-group
  {:name "A Group" :description "Description of a group" :member-guids []})

(defn create-group-request
  "Returns a hiccup representation of the SOAP body for a GetGroups2 request using the provided itoken."
  [param-map]
  (let [{:keys [token name description member-guids managing-group guid owner-provider-guid]} param-map]
    ["ns2:CreateGroup"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:newGroup"
        (when guid ["ns3:Guid" guid])
        ["ns3:Name" name]
        ["ns3:Description" description]
        ;; Currently need to provide at least one member.
        ["ns3:MemberGuids" (soap/item-list member-guids)]
        (when owner-provider-guid ["ns3:OwnerProviderGuid" owner-provider-guid])]
      ["ns2:managingGroupGuid" managing-group]]))

(defn get-groups-request
  "Returns a hiccup representation of the SOAP body for a GetGroups2 request using the provided itoken."
  [param-map]
  (let [{:keys [token group-guids]} param-map]
    ["ns2:GetGroups2"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:groupGuids" (soap/item-list group-guids)]]))

(defn get-group-names-by-member2
  "Returns a hiccup representation of the SOAP body for a GetGroupNamesByMember2 request using the provided itoken."
  [param-map]
  (let [{:keys [token member-guid]} param-map]
    ["ns2:GetGroupNamesByMember2"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:memberGuid" member-guid]]))

(defn create-group
  "Perform a CreateGroups request against the SOAP API. Takes a map containing request parameters:
    [:token :guid :name :description :member-guids :owner-provider-id]
    retruns the GUID of the new group."
  [param-map]
  (soap/string-from-soap-request :group2-management :create-group (create-group-request param-map)))

(defn get-groups
  "Perform a GetGroups2 request against the SOAP API.  Takes a map containing request parameters:
    [token group-guids]
    returns a list of maps containing the parameters for each group listed in group-guids."
  [param-map]
  (soap/item-map-list-from-soap-request :group2-management :get-groups2 (get-groups-request param-map) group-keys))

(defn get-group-names-by-member
  "Perform a GetGroupNamesByMember2 request against the SOAP API.  Takes a map containing request parameters:
    [token member-guid]
    and returns a list of maps containing the name and guid for each group in which the provided user is a member."
  [param-map]
  (soap/item-map-list-from-soap-request :group2-management :get-group-names-by-member2
        (get-group-names-by-member2 param-map)[:name :guid]))

(defn get-group-by-name-and-member-name
  "Convenience function to allow retrieving a group by the group name and user id of one of its members, without needing to know any guids.
    returns a map containing the group information for the matched group, if any."
  [token group-name member-name]
  (let [ member (user/get-user-by-user-name {:token token :user-name member-name})
         member-guid (:guid member)
         group (->> (get-group-names-by-member {:token token :member-guid member-guid})
                    (filter #(= (:name %) group-name))
                    (first))
         group-guid (:guid group)]
       (if group-guid
          (-> (get-groups {:token token :group-guids [group-guid]})
             (first))
          (cmr.common.services.errors/throw-service-error :soap-fault (str "Group [" group-name "] does not exist or does not include the specified user.")))))
