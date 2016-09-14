(ns cmr.umm-spec.xml-to-umm-mappings.dif9.data-contact
  "Defines mappings and parsing from DIF 9 elements into UMM records data contact fields."
  (:require
   [clojure.set :as set]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select text]]
   [cmr.umm-spec.umm-to-xml-mappings.dif9.data-contact :as dc]))

(def dif9-role->umm-contact-role
  "DIF9 role to UMM data contact role mapping."
  (merge (set/map-invert dc/umm-contact-role->dif9-role)
         {"DIF AUTHOR" "Metadata Author"}))

(defn- parse-contact-mechanisms
  "Returns UMM-C contact mechanisms from DIF9 Personnel element."
  [personnel]
  (seq (concat
         (for [email (values-at personnel "Email")]
           {:Type "Email" :Value email})
         (for [fax (values-at personnel "Fax")]
           {:Type "Fax" :Value fax})
         (for [phone (values-at personnel "Phone")]
           {:Type "Telephone" :Value phone}))))

(defn- parse-address
  "Returns UMM-C contact address from DIF9 Personnel element."
  [personnel]
  (let [addresses (seq (values-at personnel "Contact_Address/Address"))
        city (value-of personnel "Contact_Address/City")
        province-or-state (value-of personnel "Contact_Address/Province_or_State")
        postal-code (value-of personnel "Contact_Address/Postal_Code")
        country (value-of personnel "Contact_Address/Country")]
    (when (or addresses city province-or-state postal-code country)
      [{:StreetAddresses addresses
        :City city
        :StateProvince province-or-state
        :PostalCode postal-code
        :Country country}])))

(defn parse-contact-persons
  "Returns UMM-C contact persons map for the given DIF9 Personnel elements."
  ([personnels]
   (parse-contact-persons personnels dif9-role->umm-contact-role))
  ([personnels dif9-role-umm-role-mapping]
   (when personnels
     (for [personnel personnels]
       {:Roles (seq (map #(get dif9-role-umm-role-mapping % "Data Center Contact")
                         (values-at personnel "Role")))
        :ContactInformation {:ContactMechanisms (parse-contact-mechanisms personnel)
                             :Addresses (parse-address personnel)}
        :FirstName (value-of personnel "First_Name")
        :MiddleName (value-of personnel "Middle_Name")
        :LastName (value-of personnel "Last_Name")}))))
