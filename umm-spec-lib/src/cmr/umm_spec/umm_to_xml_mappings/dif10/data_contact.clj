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

(defn collection-personnel-roles
 [collection]
 (distinct
   (map
    #(get umm-personnel-contact-role->dif10-role % umm-personnel-default-contact-role)
    (map #(:Roles %) (concat (:ContactGroups collection) (:ContactPersons collection))))))

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
  [contact]
  ;; DIF9 Personnel can only have one contact address, so we only take the first contact
  ;; information and write it out. Even though theoretically the first contact group could not
  ;; have contact address, but a later contact group could, we don't think it happens
  ;; in real world use cases and just ignore the rest of the contact groups.
  (let [contact-info (first (:ContactInformation contact))
        contact-mechanisms (:ContactMechanisms contact-info)]
   [:Contact_Person
    [:First_Name (:FirstName contact)]
    [:Middle_Name (:MiddleName contact)]
    [:Last_Name (:LastName contact)]
    (contact-info->address contact-info)
    (contact-mechanisms->phones contact-mechanisms)
    (contact-mechanisms->emails contact-mechanisms)]))
    ;[:uuid (:Uuid contact)]

(defn- contact->contact-group
  [contact]
  (let [contact-info (first (:ContactInformation contact))
        contact-mechanisms (:ContactMechanisms contact-info)]
    (proto-repl.saved-values/save 30)
   [:Contact_Group
    ;[:Name (if-let [uuid (:Uuid contact)] {:uuid uuid} {}) (:GroupName contact)]
    [:Name (:GroupName contact)]
    (contact-info->address contact-info)
    (contact-mechanisms->phones contact-mechanisms)
    (contact-mechanisms->emails contact-mechanisms)]))


(defn generate-personnel
  "Returns the DIF10 personnel elements from the given umm collection or DataCenter"
  [c]
  (cond
    (seq (:ContactPersons c))
    (for [person (:ContactPersons c)]
      (contact->contact-person person))
    (seq (:ContactGroups c))
    (for [group (:ContactGroups c)]
      (contact->contact-group group))
    :else
    [:Contact_Person
     [:Last_Name u/not-provided]]))


(defn generate-collection-personnel
  "Returns the DIF10 personnel elements from the given umm collection"
  [collection]
  (when (or (some? (:ContactGroups collection)) (some? (:ContactPers collection)))
    [:Personnel
      (for [role (collection-personnel-roles collection)]
       [:Role role])
      (generate-personnel collection)]))
