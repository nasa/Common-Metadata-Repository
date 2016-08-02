(ns cmr.umm-spec.xml-to-umm-mappings.echo10.data-contact
  "Defines mappings and parsing from ECHO10 contact elements into UMM records
   data center and contact person fields."
  (:require [clojure.set :as set]
            [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]
            [cmr.umm-spec.util :as u]))

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

(defn- parse-contact-information
  [contact]
  (let [service-hours (value-of contact "HoursOfService")
        instructions (value-of contact "Instructions")]
    (when (or (some? service-hours) (some? instructions))
      {:ServiceHours service-hours
       :ContactInstruction instructions})))

(defn parse-data-centers
  [doc]
  (let [contacts (select doc "/Collection/Contacts/Contact")]
   (if (seq contacts)
    (for [contact contacts]
      {:Roles (map #(get echo10-contact-role->umm-data-center-role %) [(value-of contact "Role")])
       :ShortName (value-of contact "OrganizationName")
       :LongName (value-of contact "OrganizationName")
       :ContactInformation (parse-contact-information contact)})
    [u/not-provided-data-center])))
