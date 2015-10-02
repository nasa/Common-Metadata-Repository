(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.organizations-personnel
  "Functions for generating ISO19115-2 XML elements from UMM organization and personnel records."
  (:require [cmr.umm-spec.xml.gen :refer :all]
            [clojure.string :as str]
            [cmr.umm-spec.iso19115-2-util :as iso]
            [cmr.umm-spec.iso-keywords :as kws]))


(defn responsibility-by-role
  [responsibilities role]
  (seq (filter (fn [responsibility]
              (= (:Role responsibility) role)) responsibilities)))

(defn- contact-values-by-type
  [contacts type]
  (seq (map :Value (filter #(= (:Type %) type) contacts))))

(defn generate-online-resource-urls
  "ISO-19115 only supports one related url in a contactInfo. For now we just use the first related url. We can look into how we want to write all related urls out later."
  [online-resource-urls]
  (when-let [{:keys [URLs Description]} (first online-resource-urls)]
    (when-let [url (first URLs)]
      [:gmd:onlineResource
       [:gmd:CI_OnlineResource
        [:gmd:linkage
         [:gmd:URL url]]
        (if Description
          [:gmd:description
           (char-string Description)]
          [:gmd:description {:gco:nilReason "missing"}])
        ;; Online Resource url is hardcoded to information since the url
        ;; corresponds to information about the party
        [:gmd:function
         [:gmd:CI_OnLineFunctionCode
          {:codeList (str (:ngdc iso/code-lists) "#CI_OnLineFunctionCode")
           :codeListValue "information"} "information"]]]])))

(defn- generate-addresses
  "ISO-19115 only supports one addresses in a contactInfo. For now we just use the first address. We can look into how we want to write all addresses out later."
  [addresses]
  (when-let [{:keys [StreetAddresses City StateProvince PostalCode Country]} (first addresses)]
    [:gmd:address
     [:gmd:CI_Address
      (for [street-address StreetAddresses]
        [:gmd:deliveryPoint (char-string street-address)])
      [:gmd:city (char-string City)]
      [:gmd:administrativeArea (char-string StateProvince)]
      [:gmd:postalCode (char-string PostalCode)]
      [:gmd:country (char-string Country)]]]))

(defn- generate-emails
  [emails]
  [:gmd:address
   [:gmd:CI_Address
    (for [email emails]
      [:gmd:electronicMailAddress (char-string email)])]])

(defn generate-responsible-party
  [responsibility]
  (let [{:keys [Role] {:keys [OrganizationName Person ServiceHours
                              ContactInstructions Contacts Addresses
                              RelatedUrls]} :Party} responsibility
        role-code (str/lower-case Role)]
    [:gmd:CI_ResponsibleParty
     (when-let [{:keys [FirstName MiddleName LastName]} Person]
       [:gmd:individualName (char-string
                              (str/join
                                " " (remove nil? [FirstName MiddleName LastName])))])
     [:gmd:organisationName (char-string (:ShortName OrganizationName))]
     [:gmd:positionName {:gco:nilReason "missing"}]
     [:gmd:contactInfo
      [:gmd:CI_Contact
       (for [phone (contact-values-by-type Contacts "phone")]
         [:gmd:phone
          [:gmd:CI_Telephone
           [:gmd:voice (char-string phone)]]])
       (generate-addresses Addresses)
       (generate-emails (contact-values-by-type Contacts "email"))
       (generate-online-resource-urls RelatedUrls)
       [:gmd:hoursOfService (char-string ServiceHours)]
       [:gmd:contactInstructions (char-string ContactInstructions)]]]
     [:gmd:role
      [:gmd:CI_RoleCode {:codeList (str (:ngdc iso/code-lists) "#CI_RoleCode")
                         :codeListValue role-code} role-code]]]))