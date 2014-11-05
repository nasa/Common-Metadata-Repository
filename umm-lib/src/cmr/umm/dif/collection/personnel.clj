(ns cmr.umm.dif.collection.personnel
  "Provides functions to parse and generate DIF Personnel elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Phone
  [phone-element]
  (let [number (cx/string-at-path phone-element [:Number])
        num-type (cx/string-at-path phone-element [:Type])]
    (c/map->Phone {:number number
                   :number-type num-type})))

(defn xml-elem->Address
  [addr-element]
  (let [city (cx/string-at-path addr-element [:City])
        state-province (cx/string-at-path addr-element [:State_Province])
        country (cx/string-at-path addr-element [:Country])
        postal-code (cx/string-at-path addr-element [:Postal_Code])
        street-address-lines (cx/strings-at-path addr-element [:Street_Address])]
    (c/map->Address {:city city
                     :state-province state-province
                     :country country
                     :postal-code postal-code
                     :street-address-lines street-address-lines})))

(defn xml-elem->ContactPerson
  [person-element]
  (let [first-name (cx/string-at-path person-element [:First_Name])
        middle-name (cx/string-at-path person-element [:Middle_Name])
        last-name (cx/string-at-path person-element [:Last_Name])
        addresses (map xml-elem->Address
                       (cx/elements-at-path
                         person-element
                         [:Address]))
        phones (map xml-elem->Phone
                    (cx/elements-at-path
                      person-element
                      [:Phone]))
        emails (cx/strings-at-path person-element [:Email])]
    (c/map->ContactPerson {:first-name first-name
                           :middle-name middle-name
                           :last-name last-name
                           :addresses addresses
                           :phones phones
                           :emails emails})))

(defn xml-elem->personnel
  [collection-element]
  (let [personnel-element (cx/element-at-path collection-element [:Personnel])
        roles (cx/strings-at-path personnel-element [:Role])
        contacts (map xml-elem->ContactPerson
                      (cx/elements-at-path
                        personnel-element
                        [:Contact_Person]))
        contacts (for [index (range (count contacts))]
                   (assoc (nth contacts index) :role (nth roles index)))]
    (not-empty contacts)))

(defn generate-phone
  [phone]
  (let [{:keys [number number-type]} phone]
    (x/element :Phone {}
               (x/element :Number {} number)
               (x/element :Type {} number-type))))

(defn generate-address
  [address]
  (let [{:keys [city state-province country postal-code street-address-lines]} address]
    (x/element :Address {}
               (x/element :City {} city)
               (for [line street-address-lines]
                 (x/element :Street_Address {} line))
               (x/element :State_Province {} state-province)
               (x/element :Postal_Code {} postal-code)
               (x/element :Country {} country))))

(defn generate-contact-person
  [contact-person]
  (let [{:keys [first-name middle-name last-name addresses phones emails]} contact-person]
    (x/element :Contact_Person {}
               (when first-name
                 (x/element :First_Name {} first-name))
               (when middle-name
                 (x/element :Middle_Name {} middle-name))
               (x/element :Last_Name {} last-name)
               (map generate-address addresses)
               (map generate-phone phones)
               (for [email emails]
                 (x/element :Email {} email)))))

(defn generate-personnel
  [personnel]
  (when (not-empty personnel)
    (x/element :Personnel {}
               (for [contact-person personnel]
                 (x/element :Role {} (:role contact-person)))
               (for [contact-person personnel]
                 (generate-contact-person contact-person)))))
