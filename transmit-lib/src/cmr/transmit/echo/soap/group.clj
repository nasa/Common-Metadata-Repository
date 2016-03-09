(ns cmr.transmit.echo.soap.group
  "Helper to perform Group tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.transmit.echo.soap.user :as user]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]))

(def
  ^{:doc "Keys within a group map"}
  group-keys [:guid :name :description :member-guids :owner-provider-id :managing-group-guid])

(def
  ^{:doc "A minimally valid group map"}
  minimal-group
  {:name "A Group" :description "Description of a group" :member-guids []})

(defn create-group
  "Perform a CreateGroups request against the SOAP API. Takes a map containing request parameters:
    [:token :guid :name :description :member-guids :owner-provider-id]
    retruns the GUID of the new group."
  [param-map]
  (let [{:keys [token name description member-guids managing-group guid owner-provider-guid]} param-map
        body ["ns2:CreateGroup"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:newGroup"
                  (when guid ["ns3:Guid" guid])
                  ["ns3:Name" name]
                  ["ns3:Description" description]
                  ;; Currently need to provide at least one member.
                  ["ns3:MemberGuids" (soap/item-list member-guids)]
                  (when owner-provider-guid ["ns3:OwnerProviderGuid" owner-provider-guid])]
                ["ns2:managingGroupGuid" managing-group]]]
      (-> (soap/post-soap :group2-management body)
          (soap/extract-string :create-group))))

(defn get-groups
  "Perform a GetGroups2 request against the SOAP API.  Takes a map containing request parameters:
    [token group-guids]
    returns a list of maps containing the parameters for each group listed in group-guids."
  [param-map]
  (let [{:keys [token group-guids]} param-map
        body ["ns2:GetGroups2"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:groupGuids" (soap/item-list group-guids)]]]
      (-> (soap/post-soap :group2-management body)
          (soap/extract-item-map-list :get-groups2 group-keys))))

(defn get-group-names-by-member
  "Perform a GetGroupNamesByMember2 request against the SOAP API.  Takes a map containing request parameters:
    [token member-guid]
    and returns a list of maps containing the name and guid for each group in which the provided user is a member."
  [param-map]
  (let [{:keys [token member-guid]} param-map
        body ["ns2:GetGroupNamesByMember2"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:memberGuid" member-guid]]]
      (info "param-map = " param-map)
      (info "member-guid = " member-guid)
      (-> (soap/post-soap :group2-management body)
          (soap/extract-item-map-list :get-group-names-by-member2 [:name :guid]))))

(defn get-group-by-name-and-member-name
  "Convenience function to allow retrieving a group by the group name and user id of one of its members, without needing to know any guids.
    returns a map containing the group information for the matched group, if any."
  [token group-name member-name]
  (let [member (user/get-user-by-user-name {:token token :user-name member-name})
        member-guid (:guid member)
        group (->> (get-group-names-by-member {:token token :member-guid member-guid})
                   (filter #(= (:name %) group-name))
                   (first))
        group-guid (:guid group)]
       (if group-guid
          (-> (get-groups {:token token :group-guids [group-guid]})
             (first))
          (cmr.common.services.errors/throw-service-error :soap-fault (str "Group [" group-name "] does not exist or does not include the specified user.")))))
