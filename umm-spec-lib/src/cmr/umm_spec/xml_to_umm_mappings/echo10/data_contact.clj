
(ns cmr.umm-spec.xml-to-umm-mappings.echo10.data-contact
  "Defines mappings and parsing from ECHO10 contact elements into UMM records
   data center and contact person fields."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.umm-to-xml-mappings.echo10.data-contact :as dc]
   [cmr.umm-spec.util :as u]))

(def echo10-contact-role->umm-data-center-role
   {"ARCHIVER" "ARCHIVER"
    "Archive" "ARCHIVER"
    "archiving data center" "ARCHIVER"
    "CUSTODIAN" "ARCHIVER"
    "Data Manager" "ARCHIVER"
    "internal data center" "ARCHIVER"
    "DATA CENTER CONTACT" "ARCHIVER"
    "DISTRIBUTOR" "DISTRIBUTOR"
    "Data Originator" "ORIGINATOR"
    "author" "ORIGINATOR"
    "PROCESSOR" "PROCESSOR"
    "Producer" "PROCESSOR"})

(def default-data-center-role
  "ARCHIVER")

(def echo10-job-position->umm-contact-person-role
 {"DATA CENTER CONTACT" "Data Center Contact"
  "Primary Contact" "Data Center Contact"
  "TECHNICAL CONTACT" "Technical Contact"
  "Product Team Leader" "Technical Contact"
  "Technical Contact for Science" "Science Contact"
  "GLAS Science Team Leader" "Science Contact"
  "ICESAT Project Scientist" "Science Contact"
  "INVESTIGATOR" "Investigator"
  "Associate Principal Investigator" "Investigator"
  "METADATA AUTHOR" "Metadata Author"
  "DIF AUTHOR" "Metadata Author"
  "TECHNICAL CONTACT, DIF AUTHOR" "Metadata Author"
  "NSIDC USER Services" "User Services"
  "User Services" "User Services"
  "GHRC USER SERVICES" "User Services"
  "Science Software Development Manager" "Science Software Development"
  "Deputy Science Software Development Manager" "Science Software Development"
  "Sea Ice Algorithms" "Science Software Development"
  "Snow Algorithms" "Science Software Development"})

(def default-contact-person-role
  "Technical Contact")

(defn- parse-contact-mechanisms
  "Parse ECHO10 contact mechanisms to UMM."
  [contact]
  (seq (concat
        (for [phone (select contact "OrganizationPhones/Phone")]
          {:Type (u/correct-contact-mechanism (value-of phone "Type"))
           :Value (value-of phone "Number")})
        (for [email (values-at contact "OrganizationEmails/Email")]
          {:Type "Email"
           :Value email}))))

(defn- parse-addresses
  "Parse ECHO10 addresses to UMM"
  [contact]
  (for [address (select contact "OrganizationAddresses/Address")]
    {:StreetAddresses [(value-of address "StreetAddress")]
     :City (value-of address "City")
     :StateProvince (value-of address "StateProvince")
     :PostalCode (value-of address "PostalCode")
     :Country (value-of address "Country")}))

(defn- parse-contact-information
  "Parse ECHO10 contact information into UMM"
  [contact]
  (let [service-hours (value-of contact "HoursOfService")
        instructions (value-of contact "Instructions")
        mechanisms (parse-contact-mechanisms contact)
        addresses (parse-addresses contact)]
    (when (or service-hours instructions mechanisms addresses)
      {:ServiceHours service-hours
       :ContactInstruction instructions
       :ContactMechanisms mechanisms
       :Addresses addresses})))

(defn- parse-contact-persons
  "Parse ECHO10 Contact Persons to UMM"
  [contact sanitize?]
  (for [person (select contact "ContactPersons/ContactPerson")]
    {:Roles (remove nil? (u/map-with-default echo10-job-position->umm-contact-person-role
                                             [(value-of person "JobPosition")]
                                             default-contact-person-role
                                             sanitize?))
     :FirstName (value-of person "FirstName")
     :MiddleName (value-of person "MiddleName")
     :LastName (value-of person "LastName")}))

(defn parse-data-contact-persons
  "Parse ECHO10 contacts to UMM collection ContactPersons (as opposed to ContactPersons)
  associated with a data center). ECHO10 has Contacts/Contact which holds both data center
  and contact person info. To differentiate between a data center and a Contacts/Contact
  used to hold a list of ContactPersons not associated with a data center, get the contacts
  that have the default role and empty organization name. The ContactPersons on that Contact
  should be associated with the collection but not the data center."
  [doc sanitize?]
  (let [all-contacts (select doc "/Collection/Contacts/Contact")
        contacts (filter #(and (= (value-of % "Role") dc/default-echo10-contact-role)
                               (empty? (value-of % "OrganizationName")))
                         all-contacts)]
    (mapcat #(parse-contact-persons % sanitize?) contacts)))

(defn- parse-data-centers-from-contacts
  "Parse ECHO10 contacts to UMM data center contact persons.
  ECHO10 has Contacts/Contact which holds both data center
  and contact person info. To differentiate between a data center and a Contacts/Contact
  used to hold a list of ContactPersons not associated with a data center, remove the contacts
  that have the default role and empty organization name. The ContactPersons on that Contact
  should be associated with the collection but not the data center."
  [doc sanitize?]
  (let [all-contacts (select doc "/Collection/Contacts/Contact")
        contacts (remove #(and (= (value-of % "Role") dc/default-echo10-contact-role)
                               (empty? (value-of % "OrganizationName")))
                         all-contacts)]
    (for [contact contacts]
      {:Roles (remove nil? (u/map-with-default echo10-contact-role->umm-data-center-role
                                               [(value-of contact "Role")]
                                               default-data-center-role
                                               sanitize?))
       :ShortName (u/with-default (value-of contact "OrganizationName") sanitize?)
       :ContactInformation (parse-contact-information contact)
       :ContactPersons (parse-contact-persons contact sanitize?)})))

(defn- parse-additional-center
  "ECHO10 has both ArchiveCenter and ProcessingCenter fields which can each hold an additional
  data center. If that data center already exists in Contacts/Contact, don't create a new data
  center. Otherwise create a data center.
  The situation where the data center already exists would happen if the data was already coverted
  to UMM. When it's converted from UMM, the data is put into Contacts/Contact and ArchiveCenter or
  ProcessingCenter to avoid the loss of any data, thus there already be a record for that data center
  in UMM.

  doc - XML doc
  data-centers - data centers converted to UMM from Contacts/Contact
  center-name - name by which to find in XML - 'ArchiveCenter' or 'ProcessingCenter'
  center-role - role for the data center"
  [doc data-centers center-name center-role]
  (let [center (value-of doc (str "/Collection/" center-name))]
    (if center
      ;; Check to see if we already have an entry for this data center - the role and short name
      ;; match. If so, don't create a new record.
      (when (not-any?
             #(and (.contains (:Roles %) center-role)
                   (= (:ShortName %) center))
             data-centers)
       {:Roles [center-role]
        :ShortName center}))))

(defn parse-data-centers
  "Parse data centers from ECHO10 XML. Data center information comes from Contacts/Contact,
  ArchiveCenter, and ProcessingCenter."
  [doc sanitize?]
  (let [data-centers (parse-data-centers-from-contacts doc sanitize?)
        data-centers (conj
                      data-centers
                      (parse-additional-center doc data-centers "ArchiveCenter" "ARCHIVER")
                      (parse-additional-center doc data-centers "ProcessingCenter" "PROCESSOR"))
        data-centers (remove nil? data-centers)]
    (if (seq data-centers)
      data-centers
      (when sanitize? [u/not-provided-data-center]))))
