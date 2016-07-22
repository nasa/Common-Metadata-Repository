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

(defn collection-personnel-roles
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
  "Returns the DIF10 phone elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [phone-mechanism (filter
                         #(and (not= "Email" (:Type %))
                               (not= "Twitter" (:Type %))
                               (not= "Facebook" (:Type %)))
                         contact-mechanisms)]
    [:Phone [:Number (:Value phone-mechanism)]
            [:Type (:Type phone-mechanism)]]))


(defn- contact-info->address
  "Returns the DIF10 contact address element for the given UMM contact information"
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
  ; "Returns the DIF9 personnel element from the given umm contact group or contact person.
  ; UMM contact role to DIF9 Personnel role mappings differ depending on if the Personnel is
  ; under Data_Center or not. When it is under Data_Center, the role is mapped only to
  ; DATA CENTER CONTACT or INVESTIGATOR; otherwise (i.e. when the Personnel is under DIF),
  ; it can be mapped as defined in umm-contact-role->dif9-role."
  [contact roles]
  ;; DIF9 Personnel can only have one contact address, so we only take the first contact
  ;; information and write it out. Even though theoretically the first contact group could not
  ;; have contact address, but a later contact group could, we don't think it happens
  ;; in real world use cases and just ignore the rest of the contact groups.
  (let [contact-info (first (:ContactInformation contact))
        contact-mechanisms (:ContactMechanisms contact-info)]
   [:Personnel
     (for [role roles]
      [:Role role])
     [:Contact_Person
      [:First_Name (:FirstName contact)]
      [:Middle_Name (:MiddleName contact)]
      [:Last_Name (:LastName contact)]
      (contact-info->address contact-info)
      (contact-mechanisms->phones contact-mechanisms)
      (contact-mechanisms->emails contact-mechanisms)]]))
    ;[:uuid (:Uuid contact)]

(defn- contact->contact-group
  [contact roles]
  (let [contact-info (first (:ContactInformation contact))
        contact-mechanisms (:ContactMechanisms contact-info)]
   [:Personnel
    (for [role roles]
      [:Role role])
    [:Contact_Group
     ;[:Name (if-let [uuid (:Uuid contact)] {:uuid uuid} {}) (:GroupName contact)]
     [:Name (:GroupName contact)]
     (contact-info->address contact-info)
     (contact-mechanisms->phones contact-mechanisms)
     (contact-mechanisms->emails contact-mechanisms)]]))


(defn generate-collection-personnel
  "Returns the DIF10 personnel elements from the given umm collection or DataCenter"
  [collection]
  (if (seq (concat (:ContactGroups collection) (:ContactPersons collection)))
   (concat
     (for [person (:ContactPersons collection)]
       (contact->contact-person person (collection-personnel-roles person)))
     (for [group (:ContactGroups collection)]
       (contact->contact-group group (collection-personnel-roles group))))
   [:Personnel
    [:Role umm-personnel-default-contact-role]
    [:Contact_Person
       [:Last_Name u/not-provided]]]))

(defn generate-data-center-personnel
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
