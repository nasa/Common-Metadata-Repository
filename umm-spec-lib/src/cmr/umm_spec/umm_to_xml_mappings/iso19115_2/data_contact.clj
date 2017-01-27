(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact
  "Functions for generating ISO19115-2 XML elements from UMM DataCenters, ContactPersons and ContactGroups."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.util :refer [char-string]]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as data-contact]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.distributions-related-url :as related-url]))

(def translated-contact-mechanism-types
 {"Direct Line" "Telephone"
  "Email" "Email"
  "Fax" "Fax"
  "Mobile" "Telephone"
  "Primary" "Telephone"
  "TDD/TTY Phone" "Telephone"
  "Telephone" "Telephone"
  "U.S. toll free" "Telephone"})

(def data-center-role->iso-role
 (set/map-invert data-contact/iso-data-center-role->umm-role))

(defn- get-phone-contact-mechanisms
 "Get phone/fax contact mechanisms from contact info"
 [contact-info]
 (when-let [contact-mechanisms (:ContactMechanisms contact-info)]
  (let [contact-mechanisms
        (map #(assoc % :Type (get translated-contact-mechanism-types (:Type %)))
             (:ContactMechanisms contact-info))]
   (filter #(or (= "Telephone" (:Type %))
                (= "Fax" (:Type %)))
           contact-mechanisms))))

(defn generate-contact-info
 "Generate contact info xml from ContactInformation"
 [contact-info]
 [:gmd:contactInfo
  [:gmd:CI_Contact
   (when-let [phone-contacts (seq (get-phone-contact-mechanisms contact-info))]
     [:gmd:phone
      [:gmd:CI_Telephone
       (for [phone (filter #(= "Telephone" (:Type %)) phone-contacts)]
        [:gmd:voice (char-string (:Value phone))])
       (for [fax (filter #(= "Fax" (:Type %)) phone-contacts)]
        [:gmd:facsimile (char-string (:Value fax))])]])
   (let [address (first (:Addresses contact-info))
         emails (filter #(= "Email" (:Type %)) (:ContactMechanisms contact-info))]
     (when (or address emails)
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
        (for [email emails]
         [:gmd:electronicMailAddress (char-string (:Value email))])]]))
   (when-let [url (first (:RelatedUrls contact-info))]
     (first
      (related-url/generate-online-resource-url
       (update url :URLs #(take 1 %))
       :gmd:onlineResource)))
   (when-let [hours (:ServiceHours contact-info)]
    [:gmd:hoursOfService (char-string hours)])
   (when-let [instruction (:ContactInstruction contact-info)]
    [:gmd:contactInstructions (char-string instruction)])]])

(defn- generate-data-center
 "Generate data center XML for the data center and ISO role"
 [data-center iso-role]
 [:gmd:CI_ResponsibleParty
  [:gmd:organisationName
    (char-string (if (:LongName data-center)
                  (str (:ShortName data-center) " &gt; " (:LongName data-center))
                  (:ShortName data-center)))]
  (generate-contact-info (:ContactInformation data-center))
  [:gmd:role
   [:gmd:CI_RoleCode
     {:codeList (:ndgc iso/code-lists)
      :codeListValue iso-role} iso-role]]])

(defn generate-archive-centers
 "Archive centers are included with the data centers but also in
 /gmi:MI_Metadata/:gmd:contact/gmd:CI_ResponsibleParty"
 [data-centers]
 (let [archive-centers (filter #(contains? #{"ARCHIVER"} (:Roles %)) data-centers)]
  (seq
   (for [center archive-centers]
    [:gmd:contact
     (generate-data-center center "custodian")]))))

(defn generate-data-centers
  "Generate data center XML from DataCenters"
 [data-centers]
 (for [data-center data-centers
       role (:Roles data-center)
       :let [iso-role (get data-center-role->iso-role role)]]
   [:gmd:pointOfContact
    (generate-data-center data-center iso-role)]))
