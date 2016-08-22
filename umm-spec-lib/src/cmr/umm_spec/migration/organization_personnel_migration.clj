(ns cmr.umm-spec.migration.organization-personnel-migration
  "Contains functions for migrating organization/personnel data-centers/contact-persons
  between versions of UMM schema.
  The change from organization/personnel to data-centers/contact-persons takes place
  going from UMM JSON schema version 1.3 to 1.4"
  (:require [cmr.common.util :as util]
            [cmr.umm-spec.util :as u]))

(defn- map-data-center-role
  "TO DO: Evaluate if this is needed or fix up"
  [role]
  (if (contains? #{"ARCHIVER" "DISTRIBUTOR" "PROCESSOR" "ORIGINATOR"} role)
    role
    "ARCHIVER"))

(defn- party->contact-information
  "Convert contact information fields from a party to a vector of contact information"
  [party]
  (let [contact-information
        {:ServiceHours (:ServiceHours party)
         :ContactInstruction (:ContactInstructions party)
         :RelatedUrls (:RelatedUrls party)
         :Addresses (:Addresses party)
         :ContactMechanisms (:Contacts party)}]
    (when (seq (util/remove-nil-keys contact-information))
      [contact-information])))

(defn- person->contact-persons
  "Convert a person to a vector of contact persons. If a role is not provided,
  fill in a default role"
  [person]
  (when (seq person)
   (if (empty? (:Roles person))
     [(assoc person :Roles [u/not-provided-contact-person-role])]
     [person])))

(defn- organization->data-center
  "Convert an organization to a data center"
  [organization]
  (let [party (:Party organization)]
    {:Roles [(map-data-center-role (:Role organization))]
     :ShortName (:ShortName (:OrganizationName party))
     :LongName (:LongName (:OrganizationName party))
     :ContactInformation (party->contact-information party)
     :ContactPersons (person->contact-persons (:Person party))}))

(defn- personnel->contact-person
  "Convert a personnel record to a contact person record"
  [personnel]
  (let [party (:Party personnel)
        person (:Person party)]
    {:Roles [(:Role personnel)]
     :FirstName (:FirstName person)
     :MiddleName (:MiddleName person)
     :LastName (:LastName person)
     :Uuid (:Uuid person)
     :ContactInformation (party->contact-information party)}))

(defn organizations->data-centers
  "Convert a list of organizations to a list of data centers. If no organizations are provided
  return the default data center"
  [organizations]
  (if (seq organizations)
    (mapv organization->data-center organizations)
    [u/not-provided-data-center]))

(defn personnel->contact-persons
  "Convert a list of personnel records to a list of contact persons"
  [personnel]
  (mapv personnel->contact-person personnel))
