(ns cmr.transmit.echo.soap.provider
  "Helper to perform Provider tasks against the SOAP API."
  (:require [cmr.transmit.echo.soap.core :as soap]
            [cmr.common.xml.parse :as xp]
            [cmr.common.xml.simple-xpath :as xpath]
            [cmr.common.log :refer (debug info warn error)]
            [camel-snake-kebab.core :as csk]))

(def provider-keys
  "Keys within a provider map."
  [:provider-id :organization-name :provider-types :rest-only :discovery-urls
   :description-of-holdings :contacts :provider-schema-name :small-provider :guid])

(def provider-policy-keys
  "Keys within a provider policy map."
  [:end-point :retry-attempts :retry-wait-time :routing :ssl-enabled :ssl-certificate :ssl-last-update :order-supports-duplicate-catalog-items
   :collections-supporting-duplicate-order-items :supported-transactions :max-items-per-order :properties
   :ordering-suspended-until-date :override-notification-enabled])

(defn map->provider
  "Generate a provider xml structure from a map"
  [param-map]
  (let [{:keys [:provider-id :organization-name :provider-types :rest-only :discovery-urls :guid
                :description-of-holdings :contacts :provider-schema-name :small-provider]} param-map]
    (seq  [ ["ns3:Guid" guid]
            ["ns3:ProviderId" provider-id]
            ["ns3:OrganizationName" (or organization-name "Not Specified")]
            ["ns3:ProviderTypes" (soap/item-list (or provider-types ["CMR"]))]
            ["ns3:RestOnly" (if (nil? rest-only) true rest-only)]
            ["ns3:DiscoveryUrls" (soap/item-list (or discovery-urls ["www.example.com"]))]
            ["ns3:DescriptionOfHoldings" (or description-of-holdings "Not Specified")]
            ["ns3:Contacts"
              (soap/item-list (or (seq contacts)
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

(defn set-provider-policies
  "Sets Provider Policies for a provider.  Takes a map containing request parameters:
    [token provider-guid provider-id end-point retry-attempts retry-wait-time routing
     ssl-enabled ssl-certificate ssl-last-update order-supports-duplicate-catalog-items
     collections-supporting-duplicate-order-items supported-transactions max-items-per-order properties
     ordering-suspended-until-date override-notification-enabled]"
  [param-map]
  (let [{:keys [token provider-guid provider-id end-point retry-attempts retry-wait-time routing
                ssl-enabled ssl-certificate ssl-last-update order-supports-duplicate-catalog-items
                collections-supporting-duplicate-order-items supported-transactions max-items-per-order properties
                ordering-suspended-until-date override-notification-enabled]} param-map]
    (soap/post-soap
      :provider ["ns2:SetProviderPolicies"
                  soap/soap-ns-map
                  ["ns2:token" token]
                  (when provider-id ["ns2:providerId" provider-id])
                  (when provider-guid ["ns2:providerGuid" provider-guid])
                  ["ns2:policies"
                    ["ns3:EndPoint" end-point]
                    ["ns3:RetryAttempts" retry-attempts]
                    ["ns3:RetryWaitTime" retry-wait-time]
                    ["ns3:Routing" (or routing "ORDER_FULFILLMENT_V9")]
                    ["ns3:SslPolicy"
                      ["ns3:SslEnabled" ssl-enabled]
                      (when ssl-certificate ["ns3:SslCertificate" ssl-certificate])
                      (when ssl-last-update ["ns3:SslLastrUpdate" ssl-last-update])]
                    ["ns3:OrderSupportsDuplicateCatalogItems" order-supports-duplicate-catalog-items]
                    (when collections-supporting-duplicate-order-items
                      ["ns3:CollectionsSupportingDuplicateCatalogItems"
                        (soap/item-list collections-supporting-duplicate-order-items)])
                    ["ns3:SupportedTransactions" (soap/item-list (map csk/->SCREAMING_SNAKE_CASE_STRING supported-transactions))]
                    (when max-items-per-order ["ns3:MaxItemsPerOrder" max-items-per-order])
                    (when properties ["ns3:Properties" properties])
                    (when ordering-suspended-until-date ["ns3:OrderingSuspendedUntilDate" ordering-suspended-until-date])
                    (when override-notification-enabled ["ns3:OverrideNotificationEnabled" override-notification-enabled])]])
    nil))

(defn remove-provider-policies
  "Removes provider policies for a provider. Takes a map containing the token, provider-id, and
   provider-guid. provider-id and provider-guid are optional. If one or the other is specified they
   will indicate the provider otherwise the provider will be assumed to be one the user has logged in
   as."
  [{:keys [token provider-id provider-guid]}]
  (soap/post-soap
   :provider ["ns2:RemoveProviderPolicies"
              soap/soap-ns-map
              ["ns2:token" token]
              (when provider-id ["ns2:providerId" provider-id])
              (when provider-guid ["ns2:providerGuid" provider-guid])])
  nil)

(defn get-provider-policies
  "Get provider policies for the provider specified by the behalfOfProvider field of the specified token."
  [param-map]
  (let [{:keys [token]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:GetProviderPolicies"
                    soap/soap-ns-map
                    ["ns2:token" token]])
      (soap/extract-item-map :get-provider-policies provider-policy-keys))))

(defn get-providers-policies
  "Get a list of provider polcies for the specified providers (by guid)"
  [param-map]
  (let [{:keys [token provider-guids]} param-map]
    (->
      (soap/post-soap
        :provider ["ns2:GetProvidersPolicies"
                    soap/soap-ns-map
                    ["ns2:token" token]
                    ["ns2:providerGuids" (soap/item-list provider-guids)]])
      (soap/extract-item-map-list :get-providers-policies provider-policy-keys))))
