(ns cmr.umm-spec.xml-to-umm-mappings.iso-smap.data-contact
 "Functions to parse DataCenters and ContactPersons from ISO 19115-2"
 (:require
  [clojure.string :as str]
  [cmr.common.xml.parse :refer :all]
  [cmr.common.xml.simple-xpath :refer [select text]]
  [cmr.umm-spec.iso19115-2-util :refer [char-string-value]]
  [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.distributions-related-url :as related-url]
  [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as iso]
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

(defn- get-contact-groups
  "Group contacts by data center types vs non data center types.
   Returns 2 groups: true and false where true is data center types"
  [contacts]
  (group-by (fn [x]
             (let [role (value-of x "gmd:role/gmd:CI_RoleCode")]
               (or (= "distributor" role)
                   (= "originator" role))))
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
             contact-info (iso/parse-contact-information
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
       :DataCenter (iso/get-short-name-long-name organization-name)
       :Type :contact-group}
      {:Contact (merge
                 {:Roles (if organization-name
                          ["Data Center Contact"]
                          ["Technical Contact"])
                  :ContactInformation contact-info
                  :NonDataCenterAffiliation non-dc-affiliation}
                 (iso/parse-individual-name (or individual-name "") sanitize?))
       :DataCenter (iso/get-short-name-long-name organization-name)
       :Type :contact-person}))))

(defn- parse-metadata-authors
  "Parse ContactPersons with Metadata Author role from ISO SMAP location."
 [contacts sanitize?]
 (for [contact contacts
       :let [organization-name (char-string-value contact "gmd:organisationName")
             individual-name (char-string-value contact "gmd:individualName")
             contact-info (iso/parse-contact-information
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
                 (iso/parse-individual-name (or individual-name "") sanitize?))
       :DataCenter (iso/get-short-name-long-name organization-name)
       :Type :contact-person}))))

(defn parse-data-center
 "Parse data center XML into data centers."
 [data-center persons sanitize?]
 (when-let [organization-name (char-string-value data-center "gmd:organisationName")]
  (let [data-center-name (iso/get-short-name-long-name organization-name)
        roles (get iso-data-center-role->umm-role (value-of data-center "gmd:role/gmd:CI_RoleCode"))]
   (merge
    {:Roles (if (vector? roles)
              roles
              [roles])
     :ContactInformation (iso/parse-contact-information
                          (first (select data-center "gmd:contactInfo/gmd:CI_Contact"))
                          "DataCenterURL"
                          sanitize?)}
    (if (or data-center-name (not sanitize?))
     data-center-name
     {:ShortName util/not-provided})
    (get-data-center-contact-persons data-center-name persons)))))

(defn- parse-processor
  "Parses PROCESSOR data centers from location specific to ISO SMAP."
  [data-center-processor persons sanitize?]
  (when-let [organization-name (char-string-value data-center-processor "gmd:organisationName")]
    (let [data-center-name (iso/get-short-name-long-name organization-name)
          role (value-of data-center-processor "gmd:role/gmd:CI_RoleCode")]
      (when (= role "originator")
        (merge
         {:Roles ["PROCESSOR"]
          :ContactInformation (iso/parse-contact-information
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
