(ns cmr.umm-spec.umm-to-xml-mappings.dif10.data-contact
  "Functions for generating DIF10 XML elements from UMM data contacts."
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.util :as u]))

(def umm-personnel-contact-role->dif10-role
   {"Data Center Contact" "TECHNICAL CONTACT"
    "Technical Contact" "TECHNICAL CONTACT"
    "Investigator" "INVESTIGATOR"
    "Metadata Author" "METADATA AUTHOR"})

(def umm-personnel-default-contact-role
  "TECHNICAL CONTACT")

(def dif10-data-center-personnel-role
  "DATA CENTER CONTACT")

;; DIF10 has email and phone contact mechanisms. UMM Email goes to DIF10 email. Facebook and Twitter
;; contact mechanisms are dropped. Everything else is considered phone in DIF10.
(def dif10-non-phone-contact-mechanisms
 #{"Email" "Twitter" "Facebook"})

(defn collection-personnel-roles
 "Maps the list of UMM personnel roles (personnel not associated with a data center) to DIF 10 roles"
 [contact]
 (distinct
   (map
    #(get umm-personnel-contact-role->dif10-role % umm-personnel-default-contact-role)
    (:Roles contact))))

(defn- contact-mechanisms->emails
  "Returns the DIF10 email elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [email-mechanism (filter #(= "Email" (:Type %)) contact-mechanisms)]
    [:Email (:Value email-mechanism)]))

(defn- contact-mechanisms->phones
  "Returns the DIF10 phone elements from the given contact mechanisms by filtering out all
   non-phone types."
  [contact-mechanisms]
  (for [phone-mechanism (remove
                         #(contains? dif10-non-phone-contact-mechanisms (:Type %))
                         contact-mechanisms)]
    [:Phone [:Number (:Value phone-mechanism)]
            [:Type (:Type phone-mechanism)]]))


(defn- contact-info->address
  "Returns the DIF10 contact address element for the given UMM contact information. For personnel
   contact info, DIF10 only takes the first address."
  [contact-info]
  ;; We only write out the first address within the contact information
  (when-let [address (first (:Addresses contact-info))]
    (let [{:keys [StreetAddresses City StateProvince Country PostalCode]} address]
      [:Address
       (for [street-address StreetAddresses]
         [:Street_Address street-address])
       [:City City]
       [:State_Province StateProvince]
       [:Postal_Code PostalCode]
       [:Country Country]])))

(defn- contact->contact-person
  "Returns the DIF10 contact person elements for a data center or collection contact person"
  [contact roles]
  (let [contact-info (:ContactInformation contact)
        contact-mechanisms (:ContactMechanisms contact-info)]
   [:Personnel
     (for [role roles]
      [:Role role])
     [:Contact_Person (if-let [uuid (:Uuid contact)] {:uuid uuid} {})
      [:First_Name (:FirstName contact)]
      [:Middle_Name (:MiddleName contact)]
      [:Last_Name (:LastName contact)]
      (contact-info->address contact-info)
      (contact-mechanisms->phones contact-mechanisms)
      (contact-mechanisms->emails contact-mechanisms)]]))

(defn- contact->contact-group
  "Returns the DIF10 contact group elements for a data center or collection contact group"
  [contact roles]
  (let [contact-info (:ContactInformation contact)
        contact-mechanisms (:ContactMechanisms contact-info)]
   [:Personnel
    (for [role roles]
      [:Role role])
    [:Contact_Group (if-let [uuid (:Uuid contact)] {:uuid uuid} {})
     [:Name (:GroupName contact)]
     (contact-info->address contact-info)
     (contact-mechanisms->phones contact-mechanisms)
     (contact-mechanisms->emails contact-mechanisms)]]))


(defn generate-collection-personnel
  "Returns the DIF10 personnel elements from the given UMM collection"
  [collection]
  (concat
   (for [person (:ContactPersons collection)]
     (contact->contact-person person (collection-personnel-roles person)))
   (for [group (:ContactGroups collection)]
     (contact->contact-group group (collection-personnel-roles group)))))

(defn generate-data-center-personnel
  "Returns the Personnel (Contact Persons and Contact Groups) records for a data center.
   If no contact persons or groups exist, a dummy contact person record is created."
  [center]
  (if (seq (concat (:ContactGroups center) (:ContactPersons center)))
    (concat
     (for [person (:ContactPersons center)]
       (contact->contact-person person [dif10-data-center-personnel-role]))
     (for [group (:ContactGroups center)]
       (contact->contact-group group [dif10-data-center-personnel-role])))
    [:Personnel
     [:Role dif10-data-center-personnel-role]
     [:Contact_Person
      [:Last_Name u/not-provided]]]))
