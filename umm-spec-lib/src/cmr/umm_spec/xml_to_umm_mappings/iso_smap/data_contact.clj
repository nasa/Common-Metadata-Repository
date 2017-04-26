(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap.data-contact
 "Functions to parse DataCenters and ContactPersons from ISO 19115-2"
 (:require
  [clojure.string :as str]
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
  [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as related-url]
  [cmr.umm-spec.url :as url]
  [cmr.umm-spec.util :as util]))

(def metadata-authors-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmd:contact/gmd:CI_ResponsibleParty"
       "[gmd:role/gmd:CI_RoleCode/@codeListValue='author']"))

(def point-of-contact-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmd:identificationInfo"
       "/gmd:MD_DataIdentification/gmd:pointOfContact/gmd:CI_ResponsibleParty"))

(def processors-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata/gmd:identificationInfo"
       "/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:citedResponsibleParty"
       "/gmd:CI_ResponsibleParty"))

(def iso-data-center-role->umm-role
  {"originator" "ORIGINATOR"
   "distributor" ["DISTRIBUTOR" "ARCHIVER"]})

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
             url-link (value-of url "gmd:linkage/gmd:URL")]]
   {:URL (when url-link (url/format-url url-link sanitize?))
    :Description (char-string-value url "gmd:description")
    :URLContentType url-content-type
    :Type "HOME PAGE"}))

(defn- parse-contact-information
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
  (proto-repl.saved-values/save 5)
  (group-by (fn [x]
             (let [role (value-of x "gmd:role/gmd:CI_RoleCode")
                   individual-name (char-string-value x "gmd:individualName")]
               (or (= "distributor" role)
                   (= "originator" role))))
               ; (or (and (or (nil? individual-name)
               ;              (some? (re-matches #"(?i).*user services|science software development.*" individual-name)))
               ;          (= role "distributor"))
               ;     (and (or (nil? individual-name)
               ;              (some? (re-matches #"(?i).*user services|science software development.*" individual-name)))
               ;          (= role "originator")))))
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
   {:ContactPersons (seq (distinct (map :Contact contacts)))}))

(defn- get-short-name-long-name
 "Split the name into short name and long name. ISO has one name field so use delimeter."
 [name]
 (when name
  (when-let [names (seq (str/split name #"&gt;|>"))]
   {:ShortName (str/trim (first names))
    :LongName (when (> (count names) 1)
               (str/join " " (map str/trim (rest names))))})))

(defn- parse-individual-name
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
                           (first (select contact "gmd:contactInfo/gmd:CI_Contact"))
                           "DataContactURL"
                           sanitize?)
             non-dc-affiliation (char-string-value contact "gmd:positionName")]]
  (when (or individual-name organization-name)
    (if (or (nil? individual-name)
            (re-matches #"(?i).*user services|science software development.*" individual-name))
      {:Contact {:Roles ["User Services"]
                 :GroupName organization-name
                 :ContactInformation contact-info
                 :NonDataCenterAffiliation non-dc-affiliation}
       :DataCenter (get-short-name-long-name organization-name)
       :Type :contact-group}
      {:Contact (merge
                 {:Roles (if organization-name
                          ["Data Center Contact"]
                          ["Technical Contact"])
                  :ContactInformation contact-info
                  :NonDataCenterAffiliation non-dc-affiliation}
                 (parse-individual-name (or individual-name "") sanitize?))
       :DataCenter (get-short-name-long-name organization-name)
       :Type :contact-person}))))

(defn- parse-metadata-authors
  ""
 [contacts sanitize?]
 (for [contact contacts
       :let [organization-name (char-string-value contact "gmd:organisationName")
             individual-name (char-string-value contact "gmd:individualName")
             contact-info (parse-contact-information
                           (first (select contact "gmd:contactInfo/gmd:CI_Contact"))
                           "DataContactURL"
                           sanitize?)
             non-dc-affiliation (char-string-value contact "gmd:positionName")]]
  (when (or individual-name organization-name)
    (when-not (or (nil? individual-name)
                  (re-matches #"(?i).*user services|science software development.*" individual-name))
      {:Contact (merge
                  {:Roles ["Metadata Author"]
                   :ContactInformation contact-info
                   :NonDataCenterAffiliation non-dc-affiliation}
                 (parse-individual-name (or individual-name "") sanitize?))
       :DataCenter (get-short-name-long-name organization-name)
       :Type :contact-person}))))

(defn parse-data-center
 "Parse data center XML into data centers"
 [data-center persons sanitize?]
 (when-let [organization-name (char-string-value data-center "gmd:organisationName")]
  (let [data-center-name (get-short-name-long-name organization-name)
        roles (get iso-data-center-role->umm-role (value-of data-center "gmd:role/gmd:CI_RoleCode"))]
   (merge
    {:Roles (if (vector? roles)
              roles
              [roles])
     :ContactInformation (parse-contact-information
                          (first (select data-center "gmd:contactInfo/gmd:CI_Contact"))
                          "DataCenterURL"
                          sanitize?)}
    (if (or data-center-name (not sanitize?))
     data-center-name
     {:ShortName util/not-provided})
    (get-data-center-contact-persons data-center-name persons)))))

(defn- parse-processor
  ""
  [data-center-processor persons sanitize?]
  (when-let [organization-name (char-string-value data-center-processor "gmd:organisationName")]
    (let [data-center-name (get-short-name-long-name organization-name)
          role (value-of data-center-processor "gmd:role/gmd:CI_RoleCode")]
      (when (= role "originator")
        (merge
         {:Roles ["PROCESSOR"]
          :ContactInformation (parse-contact-information
                               (first (select data-center-processor "gmd:contactInfo/gmd:CI_Contact"))
                               "DataCenterURL"
                               sanitize?)}
         (if (or data-center-name (not sanitize?))
           data-center-name
           {:ShortName util/not-provided})
         (get-data-center-contact-persons data-center-name persons))))))

(defn- group-contacts
 "Given contact xml, split the contacts into data centers and contacts"
 [xml]
 (let [group-contacts (get-contact-groups xml)]
  {:data-centers-xml (get group-contacts true)
   :contacts-xml (get group-contacts false)}))

(defn- get-collection-contact-persons-and-groups
  "Get contact persons and contact groups not associated with a data center."
  [contacts data-centers]
  (let [data-center-shortnames (map :ShortName data-centers)
        non-data-center-contacts (remove (fn [contact]
                                           (some (fn [short-name]
                                                   (= (get-in contact [:DataCenter :ShortName]) short-name))
                                                 data-center-shortnames))
                                         contacts)
        groups (group-by :Type non-data-center-contacts)]
    {:ContactPersons (map :Contact (get groups :contact-person))}))

(defn parse-contacts
  "Parse all contacts from XML and determine if they are Data Centers, Contact Persons or
  Contact Groups."
  [xml sanitize?]
  (let [{:keys [data-centers-xml contacts-xml]} (group-contacts (select xml point-of-contact-xpath))
        metadata-authors (parse-metadata-authors (select xml metadata-authors-xpath) sanitize?)
        processors (group-contacts (select xml processors-xpath))
        all-contacts-xml (concat contacts-xml
                                 (:contacts-xml processors))
        contacts (parse-all-contacts all-contacts-xml sanitize?)
        contacts (concat metadata-authors contacts)
        data-centers (map #(parse-data-center % contacts sanitize?) data-centers-xml)
        data-centers (concat data-centers
                             (map #(parse-processor % contacts sanitize?) (:data-centers-xml processors)))
        data-centers (distinct data-centers)]
    (merge
     {:DataCenters (if (seq data-centers)
                    data-centers
                    (when sanitize?
                      [util/not-provided-data-center]))}
     (get-collection-contact-persons-and-groups contacts data-centers))))

(comment
 (do
  (def xml-path "/Users/dpzamora/tmp/tmp.xml")
  (def sample-xml (slurp xml-path))
  (parse-contacts sample-xml false)))
