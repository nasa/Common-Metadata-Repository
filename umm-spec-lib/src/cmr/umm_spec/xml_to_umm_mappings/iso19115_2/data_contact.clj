(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact
 "Functions to parse DataCenters and ContactPersons from ISO 19115-2"
 (:require
  [clojure.string :as str]
  [clojure.java.io :as io]
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
  [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as related-url]
  [cmr.umm-spec.util :as util]))

(def point-of-contact-xpath
 "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:pointOfContact/gmd:CI_ResponsibleParty")

(def distributor-xpath
 (str "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution/gmd:distributor/gmd:MD_Distributor/"
      "gmd:distributorContact/gmd:CI_ResponsibleParty"))

(def processor-xpath
 (str "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality/gmd:lineage/gmd:LI_Lineage/gmd:processStep/"
      "gmd:LI_ProcessStep/gmd:processor/gmd:CI_ResponsibleParty"))

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

(defn- get-data-center-contact-persons-and-groups
 [data-center contacts]
 (when-let [contacts (filter #(and (some? (get-in % [:DataCenter :ShortName]))
                                   (= (:ShortName data-center) (get-in % [:DataCenter :ShortName]))
                                   (= (:LongName data-center) (get-in % [:DataCenter :LongName])))
                             contacts)]
   (let [grouped-contacts (group-by :Type contacts)]
     {:ContactPersons (seq (map :Contact (get grouped-contacts :contact-person)))
      :ContactGroups (seq (map :Contact (get grouped-contacts :contact-group)))})))

(defn- get-short-name-long-name
 "Split the name into short name and long name"
 [name]
 (when name
  (let [names (str/split name #"&gt;|>")]
   {:ShortName (str/trim (first names))
    :LongName (when (> (count names) 1)
               (str/join " " (map str/trim (rest names))))})))

(defn- parse-individual-name
 "Parse an individial name into first, middle, last"
 [name]
 (let [names (str/split name #" {1,}")
       num-names (count names)]
  (if (= 1 num-names)
   {:LastName name}
   {:FirstName (first names)
    :MiddleName (str/join " " (subvec names 1 (dec num-names)))
    :LastName (last names)})))

(defn- parse-all-contacts
 "All contacts are located in the same place as the data centers - regardless if they are a contact
 person or group, whether they are standalone or associated with a data center. In this function
 we will determine whether a contact is a person or group and whether or not it is associated to
 a data center. Return a list of the following intermediate type
 {:DataCenter - data center name if one exists
  :Type - :contact-person or :contact-group
  :Contact - ContactPerson or ContactGroup with no intermediate data}"
 [contacts sanitize?]
 (for [contact contacts
       :let [organization-name (char-string-value contact "gmd:organisationName")
             individual-name (char-string-value contact "gmd:individualName")
             contact-info (parse-contact-information
                           (first (select contact "gmd:contactInfo/gmd:CI_Contact")) sanitize?)
             non-dc-affiliation (char-string-value contact "gmd:positionName")]]
  (when (or individual-name organization-name)
   (if individual-name
     {:Contact (merge
                 {:Roles (if organization-name
                          ["Data Center Contact"]
                          ["Technical Contact"])
                  :ContactInformation contact-info
                  :NonDataCenterAffiliation non-dc-affiliation}
                (parse-individual-name individual-name))
      :DataCenter (get-short-name-long-name organization-name)
      :Type :contact-person}
    {:Contact {:Roles ["User Services"]
               :GroupName organization-name
               :ContactInformation contact
               :NonDataCenterAffiliation non-dc-affiliation}
     :DataCenter (get-short-name-long-name organization-name)
     :Type :contact-group}))))

(defn parse-data-center
 "Parse data center XML into data centers"
 [data-center persons sanitize?]
 ;(proto-repl.saved-values/save 17)
 (when-let [organization-name (char-string-value data-center "gmd:organisationName")]
  (let [data-center-name (get-short-name-long-name organization-name)]
   (merge
    {:Roles [(get iso-data-center-role->umm-role (value-of data-center "gmd:role/gmd:CI_RoleCode"))]
     :ContactInformation (parse-contact-information
                          (first (select data-center "gmd:contactInfo/gmd:CI_Contact"))
                          sanitize?)}
    data-center-name
    (get-data-center-contact-persons-and-groups data-center-name persons)))))

(defn- process-duplicate-data-centers
 "Data Centers are located in several places in the ISO xml, so we want to check for data Centers
 in all of those places, but not duplicate.
 This function is to parse the data center, check if it's a dup, and return a list of data Centers
 that are not duplicates"
 [data-centers data-centers-xml sanitize?]
 (for [data-center-xml data-centers-xml
       :let [data-center (parse-data-center data-center-xml nil sanitize?)]]
  (when (not-any? #(and (= (:ShortName %) (:ShortName data-center))
                        (= (:LongName %) (:LongName data-center))
                        (= (:Roles %) (:Roles data-center)))
                data-centers)
     data-center)))

(defn- group-contacts
 "Given contact xml, split the contacts into data centers and contacts"
 [xml]
 (let [group-contacts (get-contact-groups xml)]
  {:data-centers-xml (get group-contacts true)
   :contacts-xml (get group-contacts false)}))

(defn parse-contacts
 "Parse all contacts from XML and determine if they are Data Centers, Contact Persons or
 Contact Groups"
 [xml sanitize?]
 ;(proto-repl.saved-values/save 18)
 (let [{:keys [data-centers-xml contacts-xml]} (group-contacts (select xml point-of-contact-xpath))
       additional-contacts (group-contacts (select xml "/gmi:MI_Metadata/:gmd:contact/gmd:CI_ResponsibleParty"))
       distributors (group-contacts (select xml distributor-xpath))
       processors (group-contacts (select xml processor-xpath))
       all-contacts-xml (concat contacts-xml (:contacts-xml additional-contacts) (:contacts-xml distributors)
                               (:contacts-xml processors))
       contacts (parse-all-contacts all-contacts-xml sanitize?)
       data-centers (map #(parse-data-center % contacts sanitize?) data-centers-xml)
       data-centers (concat data-centers
                            (process-duplicate-data-centers data-centers (:data-centers-xml additional-contacts) sanitize?)
                            (process-duplicate-data-centers data-centers (:data-centers-xml distributors) sanitize?)
                            (process-duplicate-data-centers data-centers (:data-centers-xml processors) sanitize?))]
  (if (seq data-centers)
   data-centers
   (when sanitize?
    [util/not-provided-data-center]))))
