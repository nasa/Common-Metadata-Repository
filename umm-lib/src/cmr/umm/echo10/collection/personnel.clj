(ns cmr.umm.echo10.collection.personnel
  "Provides functions to parse and generate DIF Personnel elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Address
  [addr-element]
  (let [city (cx/string-at-path addr-element [:City])
        state-province (cx/string-at-path addr-element [:Province_or_State])
        country (cx/string-at-path addr-element [:Country])
        postal-code (cx/string-at-path addr-element [:Postal_Code])
        street-address-lines (cx/strings-at-path addr-element [:Address])]
    (c/map->Address {:city city
                     :state-province state-province
                     :country country
                     :postal-code postal-code
                     :street-address-lines street-address-lines})))

(defn- xml-elem->phones
  "Return a list of Phone records for the parsed xml structure."
  [contact-element]
  (for [phone-element (cx/elements-at-path contact-element [:OrganizatonPhones :Phone])]
    (c/map->Phone {:number (cx/string-at-path phone-element [:Number])
                   :number-type (cx/string-at-path phone-element [:Type])})))

(defn- xml-elem->emails
  "Return a list of emails for the parsed xml structure."
  [contact-element]
  (strings-at-path contact-element [:OrganizatonEmails :Email]))

(defn- xml-elem->addresses
  "Return a list of Addresses for the parsed xml structure."
  [contact-element]
  (for [address-element (cx/elements-at-path contact-element [:OrganizatonAddresses :Address])
        city (cx/string-at-path address-element [:City])
        country (cx/string-at-path address-element [:Country])
        postal-code (cx/string-at-path address-element [:PostalCode])
        state-province (cx/string-at-path address-element [:StateProvince])
        street-address-lines [(string-at-path address-element [:StreetAddress])]]
    (c/map->Address {:city city
                     :country country
                     :postal-code postal-code
                     :state-province state-province
                     :street-address-lines street-address-lines})))


 roles

   ;; This entity contains the address details for each contact.
   addresses

   ;; The list of addresses of the electronic mailbox of the organization or individual.
   emails

   ;; First name of the individual which the contact applies.
   first-name

   ;; Last name of the individual which the contact applies.
   last-name

   ;; Middle name of the individual which the contact applies.
   middle-name

   ;; The list of telephone details associated with the contact.
   phones


(defn xml-elem->personnel
  [collection-element]
  (let [contacts (cx/element-at-path collection-element [:Contacts])
        roles (cx/strings-at-path pe [:Role])
        first-name (cx/string-at-path pe [:First_Name])
        middle-name (cx/string-at-path pe [:Middle_Name])
        last-name (cx/string-at-path pe [:Last_Name])
        emails (cx/strings-at-path pe [:Email])
        phones (cx/strings-at-path pe [:Phone])
        addresses (map xml-elem->Address (cx/elements-at-path pe [:Contact_Address]))]
    [(c/map->ContactPerson {:roles roles
                            :first-name first-name
                            :middle-name middle-name
                            :last-name last-name
                            :addresses (seq addresses)
                            :phones (seq (map #(c/->Phone % nil) phones))
                            :emails (seq emails)})]))

(defn generate-address
  [address]
  (let [{:keys [city state-province country postal-code street-address-lines]} address]
    (x/element :Contact_Address {}
               (for [line street-address-lines]
                 (x/element :Address {} line))
               (when city
                 (x/element :City {} city))
               (when state-province
                 (x/element :Province_or_State {} state-province))
               (when postal-code
                 (x/element :Postal_Code {} postal-code))
               (when country
                 (x/element :Country {} country)))))

(defn generate-contacts
  [personnel]
  (when (not-empty personnel)
    (x/element :Contacts {}
               (for [contact personnel]
                 (let [{:keys [roles first-name middle-name last-name emails phones address]} contact]
                   (x/element :Contact {}
                              (x/element :Role {} (first roles))


                              (let [person (first personnel)
                                    {:keys [roles first-name middle-name last-name emails phones addresses]} person]
                                (x/element :Personnel {}
                                           (for [role roles]
                                             (x/element :Role {} role))
                                           (when first-name
                                             (x/element :First_Name {} first-name))
                                           (when middle-name
                                             (x/element :Middle_Name {} middle-name))
                                           (x/element :Last_Name {} last-name)
                                           (for [email emails]
                                             (x/element :Email {} email))
                                           (for [phone phones]
                                             (x/element :Phone {} (:number phone)))
                                           (generate-address (first addresses))))))))))
