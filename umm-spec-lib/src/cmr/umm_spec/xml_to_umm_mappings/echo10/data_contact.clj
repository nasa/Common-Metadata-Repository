(ns cmr.umm-spec.xml-to-umm-mappings.echo10.data-contact
  "Defines mappings and parsing from ECHO10 contact elements into UMM records
   data center and contact person fields."
  (:require [clojure.set :as set]
            [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.umm-spec.util :as u]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.data-contact :as dc]))

(def echo10-contact-role->umm-data-center-role
   {"ARCHIVER" "ARCHIVER"
    "Archive" "ARCHIVER"
    "archiving data center" "ARCHIVER"
    "CUSTODIAN" "ARCHIVER"
    "Data Manager" "ARCHIVER"
    "internal data center" "ARCHIVER"
    "DISTRIBUTOR" "DISTRIBUTOR"
    "Data Originator" "ORIGINATOR"
    "author" "ORIGINATOR"
    "PROCESSOR" "PROCESSOR"
    "Producer" "PROCESSOR"})

(def echo10-job-position0->umm-contact-person-role
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
  "Science Software Development Manager" "Science Software"
  "Deputy Science Software Development Manager" "Science Software"
  "Sea Ice Algorithms" "Science Software"
  "Snow Algorithms" "Science Software"})

(defn- parse-contact-mechanisms
  [contact]
  (seq (concat
        (for [phone (select contact "OrganizationPhones/Phone")]
          {:Type (value-of phone "Type")
           :Value (value-of phone "Number")})
        (for [email (values-at contact "OrganizationEmails/Email")]
          {:Type "Email"
           :Value email}))))

(defn- parse-addresses
  [contact]
  (for [address (select contact "OrganizationAddresses/Address")]
    {:StreetAddresses [(value-of address "StreetAddress")]
     :City (value-of address "City")
     :StateProvince (value-of address "StateProvince")
     :PostalCode (value-of address "PostalCode")
     :Country (value-of address "Country")}))

(defn- parse-contact-information
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
  [contact]
  (for [person (select contact "ContactPersons/ContactPerson")]
    {:Roles (map #(get echo10-job-position0->umm-contact-person-role %)
              [(value-of person "JobPosition")])
     :FirstName (value-of person "FirstName")
     :MiddleName (value-of person "MiddleName")
     :LastName (value-of person "LastName")}))

(defn parse-data-contact-persons
  [doc]
  (let [all-contacts (select doc "/Collection/Contacts/Contact")
        contacts (filter #(and (= (value-of % "Role") dc/default-echo10-contact-role)
                               (empty? (value-of % "OrganizationName")))
                         all-contacts)]
    (flatten
     (for [contact contacts]
       (parse-contact-persons contact)))))

(defn parse-data-centers
  [doc]
  (let [all-contacts (select doc "/Collection/Contacts/Contact")
        contacts (remove #(and (= (value-of % "Role") dc/default-echo10-contact-role)
                               (empty? (value-of % "OrganizationName")))
                   all-contacts)]
   (if (seq contacts)
    (for [contact contacts]
      {:Roles (map #(get echo10-contact-role->umm-data-center-role %) [(value-of contact "Role")])
       :ShortName (value-of contact "OrganizationName")
       :ContactInformation (parse-contact-information contact)
       :ContactPersons (parse-contact-persons contact)})
    [u/not-provided-data-center])))
