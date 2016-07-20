(ns cmr.umm-spec.xml-to-umm-mappings.dif10.data-contact
  "Defines mappings and parsing from DIF 9 elements into UMM records data contact fields."
  (:require [clojure.set :as set]
            [cmr.common.xml.parse :refer :all]
            [cmr.common.xml.simple-xpath :refer [select text]]))
            ;[cmr.umm-spec.umm-to-xml-mappings.dif9.data-contact :as dc]))

(defn- parse-contact-mechanisms
  "Returns UMM-C contact mechanisms from DIF10 Personnel/Contact_Person element."
  [contact-person]
  (seq (concat
         (for [email (values-at contact-person "Email")]
           {:Type "Email" :Value email})
         (for [phone (select contact-person "Phone")]
           {:Type (value-of phone "Type") :Value (value-of phone "Number")}))))

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

(defn parse-contact-persons
  "Returns UMM-C contact persons map for the given DIF10 Personnel elements."
  ([personnels]
   (when personnels
      (for [personnel personnels]
        (let [roles (values-at personnel "Role")
              contact-persons (select personnel "Contact_Person")
              val (for [contact-person contact-persons]
                    {:Roles roles
                     :FirstName (value-of contact-person "First_Name")
                     :MiddleName (value-of contact-person "Middle_Name")
                     :LastName (value-of contact-person "Last_Name")
                     :Uuid (value-of contact-person "uuid")
                     :ContactInformation [{:ContactMechanisms (parse-contact-mechanisms contact-person)
                                           :Addresses (parse-address contact-person)}]})]
          (pr-str val)
          val)))))
