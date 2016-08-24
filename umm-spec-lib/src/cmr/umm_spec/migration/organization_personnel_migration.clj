(ns cmr.umm-spec.migration.organization-personnel-migration
  "Contains functions for migrating organization/personnel data-centers/contact-persons
  between versions of UMM schema.
  The change from organization/personnel to data-centers/contact-persons takes place
  going from UMM JSON schema version 1.3 to 1.4"
  (:require [cmr.common.util :as util]
            [cmr.umm-spec.util :as u]))

(def not-provided-personnel
  [{:Role "POINTOFCONTACT"
    :Party {:Person {:LastName u/not-provided}}}])

(def valid-data-center-roles
  #{"ARCHIVER" "DISTRIBUTOR" "PROCESSOR" "ORIGINATOR"})

(def valid-contact-roles
  #{"Data Center Contact", "Technical Contact", "Science Contact", "Investigator", "Metadata Author",
    "User Services", "Science Software Development"})

(def valid-responsibility-roles
  #{"RESOURCEPROVIDER", "CUSTODIAN", "OWNER", "USER", "DISTRIBUTOR", "ORIGINATOR", "POINTOFCONTACT",
    "PRINCIPALINVESTIGATOR", "PROCESSOR", "PUBLISHER", "AUTHOR", "SPONSOR", "COAUTHOR",
    "COLLABORATOR", "EDITOR", "MEDIATOR", "RIGHTSHOLDER", "CONTRIBUTOR", "FUNDER", "STAKEHOLDER"})

(defn- map-data-center-role
  "Check if the data center role is a valid UMM role. Otherwise default to ARCHIVER"
  [role]
  (get valid-data-center-roles role "ARCHIVER"))

(defn- map-contact-role
  "Check if the contact role is a valid UMM role. Otherwise default to Technical Contact"
  [role]
  (get valid-contact-roles role "Technical Contact"))

(defn- map-responsibility-type-role
  "Organization and Personnel are both a Responsibility Type, so share the same roles. Check if the
  role is a valid Responsibility Type role. If not, default to POINTOFCONTACT"
  [role]
  (get valid-responsibility-roles role "POINTOFCONTACT"))

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
     :ContactInformation (party->contact-information party)}))

(defn- contact-person->person
  "Convert a contact person record to a person record. A contact person record
  is the same as a person record, but with some extra fields"
  [contact-person]
  (-> contact-person
      (dissoc :Roles)
      (dissoc :NonDataCenterAffiliation)
      (dissoc :ContactInformation)))

(defn- contact-information->party-contact-info
  "Convert contact information to contact info part of a party object"
  [contact-info]
  {:ServiceHours (:ServiceHours contact-info)
   :ContactInstructions (:ContactInstruction contact-info)
   :Contacts (:ContactMechanisms contact-info)
   :Addresses (:Addresses contact-info)
   :RelatedUrls (:RelatedUrls contact-info)})

(defn- data-center->organization
  "Convert a data center to an organization"
  [data-center]
  {:Role (map-responsibility-type-role (first (:Roles data-center)))
   :Party (merge {:OrganizationName {:ShortName (:ShortName data-center)
                                     :LongName (:LongName data-center)}}
                 (contact-information->party-contact-info (first (:ContactInformation data-center))))})


(defn- personnel->contact-person
  "Convert a personnel record to a contact person record"
  [personnel]
  (let [party (:Party personnel)
        person (:Person party)]
    {:Roles [(map-contact-role (:Role personnel))]
     :FirstName (:FirstName person)
     :MiddleName (:MiddleName person)
     :LastName (:LastName person)
     :Uuid (:Uuid person)
     :ContactInformation (party->contact-information party)}))

(defn- contact-person->personnel
  "Convert a contact person to a personnel record"
  [contact-person]
  {:Role (map-responsibility-type-role (first (:Roles contact-person)))
   :Party (merge {:Person (contact-person->person contact-person)}
                 (contact-information->party-contact-info (first (:ContactInformation contact-person))))})

(defn organizations->data-centers
  "Convert a list of organizations to a list of data centers. If no organizations are provided
  return the default data center"
  [organizations]
  (if (seq organizations)
    (mapv organization->data-center organizations)
    [u/not-provided-data-center]))

(defn data-centers->organizations
  "Convert a list of data centers to organizations."
  [data-centers]
  (mapv data-center->organization data-centers))

(defn personnel->contact-persons
  "Convert a list of personnel records to a list of contact persons"
  [personnel]
  (mapv personnel->contact-person personnel))

(defn contact-persons->personnel
  "Convert a list of contact person records to a list of personnel records. Personnel is required
  so if no contact persons, return default personnel"
  [contact-persons]
  (if (seq contact-persons)
   (mapv contact-person->personnel contact-persons)
   not-provided-personnel))
