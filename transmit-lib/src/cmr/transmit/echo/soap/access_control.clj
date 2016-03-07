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
  (seq [["ns3:Sid"
          ["ns3:GroupSid"
            ["ns3:GroupGuid" group-guid]]]
        ["ns3:Permissions" (soap/item-list permissions)]]))

(defn generate-access-control-list
  "Build an Access Control List (ACL) based on the provided parameters.  This is pretty simplistic
    at the moment and will need to be updated as more complex ACEs are needed."
  [aces system-object-identity]
  (seq [["ns3:AccessControlEntries" (soap/item-list aces)]
        ["ns3:SystemObjectIdentity"
          ["ns3:Target" system-object-identity]]]))

(defn- create-acl-request
  "Returns a hiccup representation of the SOAP body for a CreateAcl request using the provided parameters."
  [param-map]
  (let [{:keys [token acl]} param-map]
    ["ns2:CreateAcl"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:acl" acl]]))

(defn- set-permissions-request
  "Returns a hiccup representation of the SOAP body for a SetPermissions request using the provided parameters."
  [param-map]
  (let [{:keys [token acl-guid aces replace-all]} param-map]
    ["ns2:SetPermissions"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:aclGuid" acl-guid]
      ["ns2:aces"
        (soap/item-list aces)]
      ["ns2:replaceAll" (or replace-all "false")]]))

(defn- get-acls-by-type-request
  "Returns a hiccup representation of the SOAP body for a GetAclsbyType request using the provided parameters."
  [param-map]
  (let [{:keys [token object-identity-types provider-guid-filter]} param-map]
    ["ns2:GetAclsByType"
      soap/soap-ns-map
      ["ns2:token" token]
      ["ns2:objectIdentityTypes" (soap/item-list object-identity-types)]
      ;; ns2:providerGuidFilter is required, even if it is empty
      ["ns2:providerGuidFilter" (or provider-guid-filter "")]]))

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
  (soap/string-from-soap-request :access-control :create-acl (create-acl-request param-map)))

(defn set-permissions
  "Perform a SetPermissions request against the SOAP API.  Takes a map containing request parameters:
    [token acl-guid aces replace-all]
    And has no return value."
  [param-map]
  (soap/post-soap :access-control (set-permissions-request param-map))
  nil)

(defn get-acls-by-type
  "Perform a GetAclsByType request against the SOAP API.  Takes a map containing request parameters:
    [token object-identity-types provider-guid-filter]
    And returns an array of clojure.data.xml elements representing the ACLs"
  [param-map]
  (soap/item-list-from-soap-request :access-control :get-acls-by-type (get-acls-by-type-request param-map)))

(defn remove-acls
  "Perform a RemoveAcls request against the SOAP API.  Takes a map containing request parameters:
    [token acls]
    And has no return value."
  [param-map]
  (soap/post-soap :access-control (remove-acls-request param-map)))
