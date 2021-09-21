(ns cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact
 "Functions to parse DataCenters and ContactPersons from ISO 19115-2"
 (:require
  [clojure.string :as str]
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
  [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as related-url]
  [cmr.umm-spec.url :as url]
  [cmr.umm-spec.util :as util]))

(def point-of-contact-xpath
 "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:pointOfContact/gmd:CI_ResponsibleParty")

(def cited-responsible-party-xpath
  (str "/gmi:MI_Metadata/gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/"
       "gmd:CI_Citation/gmd:citedResponsibleParty/gmd:CI_ResponsibleParty"))

(def distributor-xpath
 (str "/gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution/gmd:distributor/gmd:MD_Distributor/"
      "gmd:distributorContact/gmd:CI_ResponsibleParty"))

(def processor-xpath
 (str "/gmi:MI_Metadata/gmd:dataQualityInfo/gmd:DQ_DataQuality/gmd:lineage/gmd:LI_Lineage/gmd:processStep/"
      "gmi:LE_ProcessStep/gmd:processor/gmd:CI_ResponsibleParty"))

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

(defn- parse-urls
 [contact-info-xml url-content-type sanitize?]
 (for [url (select contact-info-xml "gmd:onlineResource/gmd:CI_OnlineResource")
       :let [name (char-string-value url "gmd:name")
             url-link (value-of url "gmd:linkage/gmd:URL")]
       :when url-link]
   {:URL (url/format-url url-link sanitize?)
    :Description (char-string-value url "gmd:description")
    :URLContentType url-content-type
    :Type "HOME PAGE"}))

(defn parse-contact-information
 "Parse contact information from XML"
 [contact-info-xml url-content-type sanitize?]
 {:ContactMechanisms (remove nil? (concat
                                         (parse-phone-contact-info contact-info-xml)
                                         (parse-email-contact contact-info-xml)))
  :Addresses [(parse-addresses contact-info-xml)]
  :RelatedUrls (parse-urls contact-info-xml url-content-type sanitize?)
  :ServiceHours (char-string-value contact-info-xml "gmd:hoursOfService")
  :ContactInstruction (char-string-value contact-info-xml "gmd:contactInstructions")})

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

(defn- get-data-center-contact-persons
 "Get the Contact Persons associated with a data center. contacts is all contacts with data center
 and type information added. Match data center on name and filter contact persons. There is
 currently no way in ISO to associate a contact group with a data center."
 [data-center contacts]
 (when-let [contacts (filter #(and (some? (get-in % [:DataCenter :ShortName]))
                                   (= (:ShortName data-center) (get-in % [:DataCenter :ShortName]))
                                   (= (:LongName data-center) (get-in % [:DataCenter :LongName]))
                                   (= :contact-person (:Type %)))
                             contacts)]
   {:ContactPersons (seq (map :Contact contacts))}))

(defn get-short-name-long-name
 "Split the name into short name and long name. ISO has one name field so use delimeter."
 [name]
 (when name
  (when-let [names (seq (str/split name #"&gt;|>"))]
   {:ShortName (str/trim (first names))
    :LongName (when (> (count names) 1)
               (str/join " " (map str/trim (rest names))))})))

(defn parse-individual-name
 "Parse an individial name into first, middle, last. ISO has one name field so use one or
 more spaces as delimeter."
 [name sanitize?]
 (let [names (str/split name #" {1,}")
       num-names (count names)]
  (case num-names
   0 (when sanitize?
      {:LastName util/not-provided})
   1 {:LastName name}
   2 {:FirstName (first names)
      :LastName (last names)}
   {:FirstName (first names)
    :MiddleName (str/join " " (subvec names 1 (dec num-names)))
    :LastName (last names)})))

(defn- parse-contacts-xml
 "All contacts are located in the same place as the data centers - regardless if they are a contact
 person or group, whether they are standalone or associated with a data center. In this function
 we will determine whether a contact is a person or group and whether or not it is associated to
 a data center. Return a list of the following intermediate type
 {:DataCenter - data center name if one exists
  :Type - :contact-person or :contact-group
  :Contact - ContactPerson or ContactGroup with no intermediate data}"
 ([contacts sanitize?]
  (parse-contacts-xml contacts nil sanitize?))
 ([contacts tech-contact-role sanitize?]
  (for [contact contacts
        :let [organization-name (char-string-value contact "gmd:organisationName")
              individual-name (char-string-value contact "gmd:individualName")
              contact-info (parse-contact-information
                            (first (select contact "gmd:contactInfo/gmd:CI_Contact"))
                            "DataContactURL"
                            sanitize?)
              non-dc-affiliation (char-string-value contact "gmd:positionName")]]
   (when (or individual-name organization-name)
    (if individual-name
      {:Contact (merge
                  {:Roles (if organization-name
                           [(or tech-contact-role "Data Center Contact")]
                           [(or tech-contact-role "Technical Contact")])
                   :ContactInformation contact-info
                   :NonDataCenterAffiliation non-dc-affiliation}
                 (parse-individual-name individual-name sanitize?))
       :DataCenter (get-short-name-long-name organization-name)
       :Type :contact-person}
     {:Contact {:Roles ["User Services"]
                :GroupName organization-name
                :ContactInformation contact-info
                :NonDataCenterAffiliation non-dc-affiliation}
      :DataCenter nil
      :Type :contact-group})))))

(defn parse-data-center
 "Parse data center XML into data centers"
 [data-center persons sanitize?]
 (when-let [organization-name (char-string-value data-center "gmd:organisationName")]
  (let [data-center-name (get-short-name-long-name organization-name)]
   (merge
    {:Roles [(get iso-data-center-role->umm-role (value-of data-center "gmd:role/gmd:CI_RoleCode"))]
     :ContactInformation (parse-contact-information
                          (first (select data-center "gmd:contactInfo/gmd:CI_Contact"))
                          "DataCenterURL"
                          sanitize?)}
    (if (or data-center-name (not sanitize?))
     data-center-name
     {:ShortName util/not-provided})
    (get-data-center-contact-persons data-center-name persons)))))

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

(defn- fix-contact-role
  "If the role is data center contact, but didn't match on a data center, set
  the role to 'Technical Contact"
  [contact-person]
  (if (= ["Data Center Contact"] (:Roles contact-person))
    (assoc contact-person :Roles ["Technical Contact"])
    contact-person))

(defn- get-collection-contact-persons-and-groups
 "Get contact persons and contact groups not associated with a data center."
 [contacts data-centers]
 (let [data-centers (distinct (map #(select-keys % [:ShortName :LongName]) data-centers))
       non-data-center-contacts (filter #(or (nil? (:DataCenter %))
                                             (not (contains? (set data-centers) (:DataCenter %))))
                                        contacts)
       groups (group-by :Type non-data-center-contacts)]
   {:ContactPersons (map fix-contact-role (map :Contact (get groups :contact-person)))
    :ContactGroups (map :Contact (get groups :contact-group))}))

(defn parse-contacts
 "Parse all contacts from XML and determine if they are Data Centers, Contact Persons or
 Contact Groups. Contacts are located in 4 different places throughout the XML, with the main place
 being point-of-contact-xpath. ISO does not distinguish between contact persons, contact groups, and
 data centers. Roles are used to determine data center. Here we need to get all of the contacts,
 separate the data centers from contact persons, remove duplicate data centers, associate contact
 persons to data centers if applicable, and determine which are standalone contact persons and contact
 groups."
 [xml sanitize?]
 (let [{:keys [data-centers-xml contacts-xml]} (group-contacts (select xml point-of-contact-xpath))
       additional-contacts (group-contacts (select xml "/gmi:MI_Metadata/:gmd:contact/gmd:CI_ResponsibleParty"))
       cited-resp-party-contacts (group-contacts (select xml cited-responsible-party-xpath))
       distributors (group-contacts (select xml distributor-xpath))
       processors (group-contacts (select xml processor-xpath))
       contacts (concat (parse-contacts-xml contacts-xml sanitize?)
                        (parse-contacts-xml (:contacts-xml additional-contacts) "Metadata Author" sanitize?)
                        (parse-contacts-xml (:contacts-xml distributors) sanitize?)
                        (parse-contacts-xml (:contacts-xml processors) sanitize?)
                        (parse-contacts-xml (:contacts-xml cited-resp-party-contacts) sanitize?))
       ;contacts (parse-contacts-xml all-contacts-xml sanitize?)
       data-centers (map #(parse-data-center % contacts sanitize?) data-centers-xml)
       data-centers (concat data-centers
                            (process-duplicate-data-centers data-centers (:data-centers-xml additional-contacts) sanitize?)
                            (process-duplicate-data-centers data-centers (:data-centers-xml distributors) sanitize?)
                            (process-duplicate-data-centers data-centers (:data-centers-xml processors) sanitize?)
                            (process-duplicate-data-centers data-centers (:data-centers-xml cited-resp-party-contacts) sanitize?))
       data-centers (remove nil? data-centers)]
  (merge
   {:DataCenters (if (seq data-centers)
                  data-centers
                  (when sanitize?
                   [util/not-provided-data-center]))}
   (get-collection-contact-persons-and-groups contacts data-centers))))
