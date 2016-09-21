(ns cmr.umm-spec.xml-to-umm-mappings.dif10.data-contact
  "Defines mappings and parsing from DIF10 elements into UMM records data contact fields."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.util :as su]))

(def dif10-role->umm-personnel-contact-role
   {"TECHNICAL CONTACT" "Technical Contact"
    "INVESTIGATOR" "Investigator"
    "METADATA AUTHOR" "Metadata Author"
    "DATA CENTER CONTACT" "Data Center Contact"})

(def dif10-data-center-personnel-role
  "Data Center Contact")

(defn collection-personnel-roles
  "Maps the list of DIF 10 roles for personnel not associated with a data center to UMM personnel roles "
 [roles]
 (distinct
   (map #(get dif10-role->umm-personnel-contact-role %) roles)))

(defn- parse-contact-mechanisms
  "Returns UMM-C contact mechanisms from DIF10 Personnel/Contact_Person or Personnel/Contact_Group element."
  [contact]
  (seq (concat
         (for [email (values-at contact "Email")]
           {:Type "Email" :Value email})
         (for [phone (select contact "Phone")]
           {:Type (su/correct-contact-mechanism (value-of phone "Type")) :Value (value-of phone "Number")}))))

(defn- parse-address
  "Returns UMM-C contact address from DIF10 Personnel Contact Person or Contact Group element."
  [contact]
  (let [addresses (seq (values-at contact "Address/Street_Address"))
        city (value-of contact "Address/City")
        state (value-of contact "Address/State_Province")
        postal-code (value-of contact "Address/Postal_Code")
        country (value-of contact "Address/Country")]
    (when (or addresses city state postal-code country)
      [{:StreetAddresses addresses
        :City city
        :StateProvince state
        :PostalCode postal-code
        :Country country}])))

(defn parse-contact-groups
  "Returns UMM-C contact groups map for the given DIF10 Personnel elements."
  [personnels]
  (seq
   (for [personnel personnels
         :let [roles (collection-personnel-roles (values-at personnel "Role"))
               contact-group (first (select personnel "Contact_Group"))]
         :when contact-group]
     {:Roles roles
      :GroupName (value-of contact-group "Name")
      :Uuid (:uuid (:attrs (first (filter #(= :Contact_Group (:tag %)) (:content personnel)))))
      :ContactInformation {:ContactMechanisms (parse-contact-mechanisms contact-group)
                           :Addresses (parse-address contact-group)}})))


(defn parse-contact-persons
  "Returns UMM-C contact persons map for the given DIF10 Personnel elements."
  [personnels sanitize?]
  (seq
   (for [personnel personnels
         :let [roles (collection-personnel-roles (values-at personnel "Role"))
               contact-person (first (select personnel "Contact_Person"))]
         :when contact-person]
     {:Roles roles
      :FirstName (value-of contact-person "First_Name")
      :MiddleName (value-of contact-person "Middle_Name")
      :LastName (su/with-default (value-of contact-person "Last_Name") sanitize?)
      :Uuid (:uuid (:attrs (first (filter #(= :Contact_Person (:tag %)) (:content personnel)))))
      :ContactInformation {:ContactMechanisms (parse-contact-mechanisms contact-person)
                           :Addresses (parse-address contact-person)}})))
