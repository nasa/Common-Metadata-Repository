(ns cmr.transmit.echo.soap.access-control
  "Helper to perform Access Control tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))

(defn generate-access-control-entry
  "Build an Access Control Entry (ACE) based on the provided parameters.  This is pretty simplistic
    at the moment and will need to be updated as more complex ACEs are needed."
  [group-guid permissions]
  ;; The seq here is a hack to get this to be processed right when we turn it into XML.  A wrapping
  ;; vector with no element name string causes an error, but a sequence of vectors works properly.
  ;; Update this if we think of a better way.
  (seq [["ns3:Sid"
          (if (#{"GUEST", "REGISTERED"} group-guid)
            ["ns3:UserAuthorizationTypeSid"
              ["ns3:UserAuthorizationType" group-guid]]
            ["ns3:GroupSid"
              ["ns3:GroupGuid" group-guid]])]
        ["ns3:Permissions" (soap/item-list permissions)]]))

(defn generate-system-access-control-list
  "Build a System Object  Access Control List (ACL) based on the provided parameters.  This is pretty simplistic
    at the moment and will need to be updated as more complex ACEs are needed."
  [aces target]
  ["ns2:acl" ["ns3:AccessControlEntries" (soap/item-list aces)]
             ["ns3:SystemObjectIdentity"
                ["ns3:Target" target]]])

(defn generate-provider-access-control-list
  "Build a Provider Object Access Control List (ACL) based on the provided parameters.  This is pretty simplistic
    at the moment and will need to be updated as more complex ACEs are needed."
  [aces provider-guid target]
  ["ns2:acl" ["ns3:AccessControlEntries" (soap/item-list aces)]
             ["ns3:ProviderObjectIdentity"
              ["ns3:ProviderGuid" provider-guid]
              ["ns3:Target" target]]])

(defn generate-catalog-item-access-control-list
  "Build a Catalog Item Access Control List (ACL) based on the provided parameters.  This is pretty simplistic
    at the moment and will need to be updated as more complex ACEs are needed."
  [aces name provider-guid collection-applicable granule-applicable]
  ["ns2:acl" ["ns3:AccessControlEntries" (soap/item-list aces)]
             ["ns3:CatalogItemIdentity"
              ["ns3:Name" name]
              ["ns3:ProviderGuid" provider-guid]
              ["ns3:CollectionApplicable" collection-applicable]
              ["ns3:GranuleApplicable" granule-applicable]]])

(defn- remove-acls-request
  "Returns a hiccup representation of the SOAP body for a RemoveAcls request using the provided parameters."
  [param-map]
  (let [{:keys [token guids]} param-map]
    ["ns2:RemoveAcls"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:guids" (soap/item-list guids)]]))

(defn create-acl
  "Perform a SetPermissions request against the SOAP API. Takes a map containing request parameters:
    [token acl]
    And returns the GUID of the new ACL."
  [param-map]
  (let [{:keys [token acl]} param-map
        body ["ns2:CreateAcl"
                soap/soap-ns-map
                ["ns2:token" token]
                acl]]
      (-> (soap/post-soap :access-control body)
          (soap/extract-string :create-acl))))

(defn set-permissions
  "Perform a SetPermissions request against the SOAP API.  Takes a map containing request parameters:
    [token acl-guid aces replace-all]
    And returns nil."
  [param-map]
  (let [{:keys [token acl-guid aces replace-all]} param-map
        body ["ns2:SetPermissions"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:aclGuid" acl-guid]
                ["ns2:aces"
                  (soap/item-list aces)]
                ["ns2:replaceAll" (or replace-all "false")]]]
      (soap/post-soap :access-control body)
      nil))

(defn get-acls-by-type
  "Perform a GetAclsByType request against the SOAP API.  Takes a map containing request parameters:
    [token object-identity-types provider-guid-filter]
    And returns an array of clojure.data.xml elements representing the ACLs"
  [param-map]
  (let [{:keys [token object-identity-types provider-guid-filter]} param-map
        body ["ns2:GetAclsByType"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:objectIdentityTypes" (soap/item-list object-identity-types)]
                ["ns2:providerGuidFilter" (or provider-guid-filter {"xsi:nil" true})]]]
      (-> (soap/post-soap :access-control body)
          (soap/extract-item-list :get-acls-by-type))))

(defn remove-acls
  "Perform a RemoveAcls request against the SOAP API.  Takes a map containing request parameters:
    [token acls]
    And returns nil."
  [param-map]
  (let [{:keys [token guids]} param-map
        body ["ns2:RemoveAcls"
                soap/soap-ns-map
                ["ns2:token" token]
                ["ns2:guids" (soap/item-list guids)]]]
    (soap/post-soap :access-control body)
    nil))
