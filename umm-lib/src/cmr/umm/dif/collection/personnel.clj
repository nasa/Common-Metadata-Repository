(ns cmr.umm.dif.collection.personnel
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

(defn xml-elem->personnel
  [collection-element]
  (let [pe (cx/element-at-path collection-element [:Personnel])
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

(defn generate-personnel
  [personnel]
  (when (not-empty personnel)
    ;; DIF currently supports only one contact point/person - future DIF schemas will support
    ;; more
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
                 (generate-address (first addresses))))))
