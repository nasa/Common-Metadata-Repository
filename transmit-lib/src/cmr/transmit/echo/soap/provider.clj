(ns cmr.transmit.echo.soap.provider
  "Helper to perform Provider tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]))

(def provider-keys
  "Keys within a provider map."
  [:provider-id :organization-name :provider-types :rest-only :discovery-urls
   :description-of-holdings :contacts :provider-schema-name :small-provider :guid])

(defn map->provider
  "Generate a provider xml structure from a map"
  [param-map]
  (let [{:keys [:provider-id :organization-name :provider-types :rest-only :discovery-urls :guid
                :description-of-holdings :contacts :provider-schema-name :small-provider]} param-map]
    (seq  [ ["ns3:Guid" (or guid nil)]
            ["ns3:ProviderId" provider-id]
            ["ns3:OrganizationName" (or organization-name "Not Specified")]
            ["ns3:ProviderTypes" (soap/item-list (or provider-types ["CMR"]))]
            ["ns3:RestOnly" (or rest-only true)]
            ["ns3:DiscoveryUrls" (soap/item-list (or discovery-urls ["www.example.com"]))]
            ["ns3:DescriptionOfHoldings" (or description-of-holdings "Not Specified")]
            ["ns3:Contacts"
              (soap/item-list (or contacts
                                  [["ns3:Item"
                                    ["ns3:Role" "Default Contact"]
                                    ["ns3:FirstName" "First"]
                                    ["ns3:LastName" "Last"]
                                    ["ns3:Email" "none@example.com"]]]))]])))


(defn create-provider
  "Creates a provider.  Takes a map containing request parameters:
    [:token :provider-id :organization-name :provider-types :rest-only :discovery-urls
      :description-of-holdings :contacts :provider-schema-name :small-provider ]"
  [param-map]
  (let [{:keys [token provider-id organization-name provider-types rest-only discovery-urls
                description-of-holdings contacts provider-schema-name small-provider]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:CreateProvider"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:newProvider" (map->provider param-map)]
                    ["ns2:providerSchemaName" (or provider-schema-name {"xsi:nil" true})]
                    ["ns2:smallProvider" (or small-provider "false")]])
      (soap/extract-string :create-provider))))

(defn update-providers
  "Updates one or more providers.  Takes a map containing:
    {:token :providers}
   Where :providers is an array of provider maps each in the form:
    {:guid :description-of-holdings :contacts :provider-schema-name :small-provider}
    :guid MUST be specified"
  [param-map]
  (let [{:keys [token providers]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:UpdateProviders"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:providers"
                      (soap/item-list
                        (map map->provider providers))]])
      (soap/extract-string :update-providers))))


(defn get-provider-names
  "Get a list of the names and guids of the specified providers (by guid) in ECHO"
  [param-map]
  (let [{:keys [token guids]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:GetProviderNames"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:guids" (if guids (soap/item-list guids) {"xsi:nil" true})]])
      (soap/extract-item-map-list :get-provider-names [:name :guid]))))

(defn get-all-providers
  "Get a list of the names and guids of all providers in ECHO"
  [token]
  (get-provider-names {:token token :guids nil}))

(defn get-provider-names-by-provider-id
  "Get a list of the names and guids of the specified providers (by provider ID) in ECHO"
  [param-map]
  (let [{:keys [token provider-ids]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:GetProviderNamesByProviderId"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:providerIds" (soap/item-list provider-ids)]])
      (soap/extract-item-map-list :get-provider-names-by-provider-id [:name :guid]))))

(defn get-providers
  "Get a list of provider info of the specified providers (by guid) in ECHO"
  [param-map]
  (let [{:keys [token provider-guids]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:GetProviders"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:providerGuids" (soap/item-list provider-guids)]])
      (soap/extract-item-map-list :get-providers provider-keys))))

(defn remove-provider
  "Remove the provider with the specified guid."
  [param-map]
  (let [{:keys [token provider-guid]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:RemoveProvider"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:providerGuid" provider-guid]])
      (soap/extract-string :remove-provider))))

(defn force-remove-provider
  "Remove the provider with the specified guid, and cascade the delete to provider owned objects."
  [param-map]
  (let [{:keys [token provider-guid]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:ForceRemoveProvider"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:providerGuid" provider-guid]])
      (soap/extract-string :force-remove-provider))))
