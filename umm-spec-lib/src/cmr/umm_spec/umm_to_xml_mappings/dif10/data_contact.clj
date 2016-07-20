(ns cmr.umm-spec.umm-to-xml-mappings.dif10.data-contact
  "Functions for generating DIF10 XML elements from UMM data contacts."
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.util :as u]))

(defn- contact-mechanisms->emails
  "Returns the DIF10 email elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [email-mechanism (filter #(= "Email" (:Type %)) contact-mechanisms)]
    [:Email (:Value email-mechanism)]))

(defn- contact-mechanisms->phones
  "Returns the DIF10 phone elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [phone-mechanism (filter #(not= "Email" (:Type %)) contact-mechanisms)]
    [:Phone [:Number (:Value phone-mechanism)]
            [:Type (:Type phone-mechanism)]]))


; (defn- contact-info->address
;   "Returns the DIF9 contact address element for the given UMM contact information"
;   [contact-info]
;   ;; We only write out the first address within the contact information
;   (when-let [address (first (:Addresses contact-info))]
;     (let [{:keys [StreetAddresses City StateProvince Country PostalCode]} address]
;       [:Contact_Address
;        (for [street-address StreetAddresses]
;          [:Address street-address])
;        [:City City]
;        [:Province_or_State StateProvince]
;        [:Postal_Code PostalCode]
;        [:Country Country]])))
;
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
        ;[:uuid (:Uuid contact)
        (contact-mechanisms->phones contact-mechanisms)
        (contact-mechanisms->emails contact-mechanisms)]))
         ;[(contact-info->address contact-info)]))))

(defn generate-personnel
  "Returns the DIF10 personnel elements from the given umm collection or DataCenter"
  ; ([c]
  ;  (generate-personnel c umm-contact-role->dif9-role))
  ; ([c umm-role-dif9-role-mapping]
  ;  (for [contact-group (concat (:ContactGroups c) (:ContactPersons c))]
  ;    (contact->personnel contact-group umm-role-dif9-role-mapping))))
  [c]
  (if (or (seq (:ContactPersons c)) (seq (:ContactGroups c)))
    (do
     (for [person (:ContactPersons c)]
      (contact->contact-person person)))
     ; TO DO: Groups
    [:Contact_Person
      [:Last_Name u/not-provided]]))
