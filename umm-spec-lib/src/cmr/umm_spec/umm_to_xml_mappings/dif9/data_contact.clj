(ns cmr.umm-spec.umm-to-xml-mappings.dif9.data-contact
  "Functions for generating DIF9 XML elements from UMM data contacts."
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.util :as u]))

(def umm-contact-role->dif9-role
  "Mapping of umm data contact role to DIF9 role"
  {"Data Center Contact" "DATA CENTER CONTACT"
   "Technical Contact" "TECHNICAL CONTACT"
   "Investigator" "INVESTIGATOR"
   "Metadata Author" "METADATA AUTHOR"})

(def umm-contact-phone-types
  "A list of UMM contact mechanism types that translates into DIF9 phone"
  #{"Direct Line" "Mobile" "Primary" "TDD/TTY Phone" "Telephone" "U.S. toll free"})

(defn- contact-mechanisms->emails
  "Returns the DIF9 email elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [email-mechanism (filter #(= "Email" (:Type %)) contact-mechanisms)]
    [:Email (:Value email-mechanism)]))

(defn- contact-mechanisms->phones
  "Returns the DIF9 phone elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [phone-mechanism (filter #(umm-contact-phone-types (:Type %)) contact-mechanisms)]
    [:Phone (:Value phone-mechanism)]))

(defn- contact-mechanisms->faxes
  "Returns the DIF9 fax elements from the given contact mechanisms"
  [contact-mechanisms]
  (for [fax-mechanism (filter #(= "Fax" (:Type %)) contact-mechanisms)]
    [:Fax (:Value fax-mechanism)]))

(defn- contact-info->address
  "Returns the DIF9 contact address element for the given UMM contact information"
  [contact-info]
  ;; We only write out the first address within the contact information
  (when-let [address (first (:Addresses contact-info))]
    (let [{:keys [StreetAddresses City StateProvince Country PostalCode]} address]
      [:Contact_Address
       (for [street-address StreetAddresses]
         [:Address street-address])
       [:City City]
       [:Province_or_State StateProvince]
       [:Postal_Code PostalCode]
       [:Country Country]])))

(defn- contact->personnel
  "Returns the DIF9 personnel element from the given umm contact group or contact person.
  UMM contact role to DIF9 Personnel role mappings differ depending on if the Personnel is
  under Data_Center or not. When it is under Data_Center, the role is mapped only to
  DATA CENTER CONTACT or INVESTIGATOR; otherwise (i.e. when the Personnel is under DIF),
  it can be mapped as defined in umm-contact-role->dif9-role."
  [contact umm-role-dif9-role-mapping]
  ;; DIF9 Personnel can only have one contact address, so we only take the first contact
  ;; information and write it out. Even though theoretically the first contact group could not
  ;; have contact address, but a later contact group could, we don't think it happens
  ;; in real world use cases and just ignore the rest of the contact groups.
  (let [contact-info (:ContactInformation contact)
        contact-mechanisms (:ContactMechanisms contact-info)]
    (vec (concat
           [:Personnel
            (for [role (distinct (map #(get umm-role-dif9-role-mapping % "DATA CENTER CONTACT")
                                      (:Roles contact)))]
              [:Role role])]
           (if-let [group-name (:GroupName contact)]
             [[:Last_Name group-name]]
             [[:First_Name (:FirstName contact)]
              [:Middle_Name (:MiddleName contact)]
              [:Last_Name (:LastName contact)]])
           (contact-mechanisms->emails contact-mechanisms)
           (contact-mechanisms->phones contact-mechanisms)
           (contact-mechanisms->faxes contact-mechanisms)
           [(contact-info->address contact-info)]))))

(defn generate-personnel
  "Returns the DIF9 personnel elements from the given umm collection or DataCenter"
  ([c]
   (generate-personnel c umm-contact-role->dif9-role))
  ([c umm-role-dif9-role-mapping]
   (for [contact-group (concat (:ContactGroups c) (:ContactPersons c))]
     (contact->personnel contact-group umm-role-dif9-role-mapping))))
