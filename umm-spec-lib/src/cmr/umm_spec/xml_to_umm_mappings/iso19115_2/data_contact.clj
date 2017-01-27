(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact
 "Functions to parse DataCenters and ContactPersons from ISO 19115-2"
 (:require
  [clojure.string :as str]
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
  [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as related-url]
  [cmr.umm-spec.util :as util]))

(def contact-xpath
 "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:pointOfContact/gmd:CI_ResponsibleParty")

(def iso-data-center-role->umm-role
 {"custodian" "ARCHIVER"
  "originator" "ORIGINATOR"
  "processor" "PROCESSOR"
  "distributor" "DISTRIBUTOR"})

(defn- parse-phone-contact-info
 "Parse phone and fax contact mechanisms from gmd:contactInfo/gmd:CI_Contact"
 [contact-info-xml]
 (when-let [phone (first (select contact-info-xml "gmd:phone/gmd:CI_Telephone"))]
  (seq
   (remove nil?
     (concat
       (for [voice (select phone "gmd:voice")]
         {:Type "Telephone"
          :Value (value-of voice "gco:CharacterString")})
       (for [fax (select phone "gmd:facsimile")]
         {:Type "Fax"
          :Value (value-of fax "gco:CharacterString")}))))))

(defn- parse-email-contact
 "Parse contact email addresses from address in gmd:contactInfo/gmd:CI_Contact"
 [contact-info-xml]
 (when-let [address (first (select contact-info-xml "gmd:address/gmd:CI_Address"))]
  (for [email (select address "gmd:electronicMailAddress")]
   {:Type "Email"
    :Value (value-of email "gco:CharacterString")})))

(defn- parse-addresses
 "Parse address from gmd:contactInfo/gmd:CI_Contact xml"
 [contact-info-xml]
 (when-let [address (first (select contact-info-xml "gmd:address/gmd:CI_Address"))]
  {:StreetAddresses (for [street-address (select address "gmd:deliveryPoint")]
                      (value-of street-address "gco:CharacterString"))
   :City (char-string-value address "gmd:city")
   :StateProvince (char-string-value address "gmd:administrativeArea")
   :PostalCode (char-string-value address "gmd:postalCode")
   :Country (char-string-value address "gmd:country")}))

(defn- parse-contact-information
 "Parse contact information from XML"
 [contact-info-xml sanitize?]
 {:ContactMechanisms (remove nil? (concat
                                         (parse-phone-contact-info contact-info-xml)
                                         (parse-email-contact contact-info-xml)))
  :Addresses [(parse-addresses contact-info-xml)]
  :RelatedUrls (related-url/parse-online-urls contact-info-xml "gmd:onlineResource/gmd:CI_OnlineResource" sanitize?)
  :ServiceHours (char-string-value contact-info-xml "gmd:hoursOfService")
  :ContactInstruction (char-string-value contact-info-xml "gmd:contactInstructions")})

(defn point-of-contact->contact-person
 [xml]
 {:LastName (char-string-value xml "gmd:pointOfContact/gmd:CI_ResponsibleParty/gmd:individualName")
  :ContactInformation (parse-contact-information (first (select xml "gmd:pointOfContact/gmd:CI_ResponsibleParty/gmd:contactInfo/gmd:CI_Contact")))})

(defn- get-contact-groups
 "Group contacts by data center types vs non data center types.
  Returns 2 groups: true and false where true is data center types"
 [contacts]
 (group-by (fn [x]
            (let [role (value-of x "gmd:role/gmd:CI_RoleCode")]
             (or (= role "custodian")
                 (= role "distributor")
                 (= role "originator")
                 (= role "processor"))))
           contacts))

(defn- get-short-name-long-name
 "Split the name into short name and long name"
 [name]
 (let [names (str/split name #"&gt;")]
  {:ShortName (str/trim (first names))
   :LongName (when (> (count names) 1)
              (str/join " " (map str/trim (rest names))))}))

(defn- data-center-contact-persons
 [data-center-name persons])

(defn parse-data-center
 "Parse data center XML into data centers"
 [data-center persons sanitize?]
 (let [organization-name (char-string-value data-center "gmd:organisationName")]
  (merge
   {:Roles [(get iso-data-center-role->umm-role (value-of data-center "gmd:role/gmd:CI_RoleCode"))]
    :ContactInformation (parse-contact-information
                         (first (select data-center "gmd:contactInfo/gmd:CI_Contact"))
                         sanitize?)}
   (get-short-name-long-name organization-name))))

(defn parse-contacts
 "Parse all contacts from XML and determine if they are Data Centers, Contact Persons or
 Contact Groups"
 [xml sanitize?]
 (let [contacts (select xml contact-xpath)
       group-contacts (get-contact-groups contacts)
       data-centers-xml (get group-contacts true)
       contact-persons (get group-contacts false)
       data-centers (map #(parse-data-center % contact-persons sanitize?) data-centers-xml)]
  (if (seq data-centers)
   data-centers
   (when sanitize?
    [util/not-provided-data-center]))))
