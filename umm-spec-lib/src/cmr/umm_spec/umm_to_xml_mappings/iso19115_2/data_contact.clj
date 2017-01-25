(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact
  "Functions for generating ISO19115-2 XML elements from UMM DataCenters, ContactPersons and ContactGroups."
  (:require
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.util :refer [char-string]]))

(def contact-info
 {:ContactMechanisms [{:Value "1 301 614 5710 x",
                       :Type "Telephone"}
                      {:Value "1 301 614 5644 x",
                       :Type "Fax"}
                      {:Value "per.gloersen@gsfc.nasa.gov",
                       :Type "Email"}]
  :ServiceHours nil,
  :Addresses [{:City "Greenbelt",
               :StateProvince "MD",
               :StreetAddresses ["Laboratory for Hydrospheric Processes"
                                 "NASA/Goddard Space Flight Center"
                                 "Code 971",]
               :PostalCode "20771"}]
  :RelatedUrls [{:URLs ["http://nsidc.org"]}
                :Description nil],
  :ContactInstruction nil})


(defn- get-phone-contact-mechanisms
 "Get phone/fax contact mechanisms from contact info"
 [contact-info]
 (filter #(or (= "Telephone" (:Type %))
              (= "Fax" (:Type %)))
         (:ContactMechanisms contact-info)))

(defn generate-contact-info
 [contact-info]
 [:gmd:contactInfo
  [:gmd:CI_Contact
   (when-let [phone-contacts (get-phone-contact-mechanisms contact-info)]
     [:gmd:phone
      [:gmd:CI_Telephone
       (for [phone (filter #(= "Telephone" (:Type %)) phone-contacts)]
        [:gmd:voice (char-string (:Value phone))])
       (for [fax (filter #(= "Fax" (:Type %)) phone-contacts)]
        [:gmd:fascimile (char-string (:Value fax))])]])
   (when-let [address (first (:Addresses contact-info))]
     [:gmd:address
      [:gmd:CI_Address
       (for [street-address (:StreetAddresses address)]
        [:gmd:deliveryPoint (char-string street-address)])
       (when-let [city (:City address)]
        [:gmd:city (char-string city)])
       (when-let [state (:StateProvince address)]
        [:gmd:administrativeArea (char-string state)])
       (when-let [postal-code (:PostalCode address)]
        [:gmd:postalCode (char-string postal-code)])
       (when-let [country (:Country address)]
        [:gmd:country (char-string country)])
       (for [email (filter #(= "Email" (:Type %)) (:ContactMechanisms contact-info))]
        [:gmd:electronicMailAddress (char-string (:Value email))])]])
   (when-let [url (first (:RelatedUrls contact-info))]
    [:gmd:onlineResource
     [:gmd:CI_OnlineResource
      [:gmd:linkage
       [:gmd:URL (first (:URLs url))]]
      (when-let [description (:Description url)]
       [:gmd:description (char-string description)])]])]])
