(ns cmr.umm.dif10.collection.personnel
  "Provides functions to parse and generate DIF 10 Personnel elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.generator-util :as gu]
            [cmr.umm.umm-collection :as c]))

(def personnel-roles
  "DIF 10 schema allows the following strings for personnel roles"
  #{"INVESTIGATOR"
    "INVESTIGATOR, TECHNICAL CONTACT"
    "METADATA AUTHOR"
    "METADATA AUTHOR, TECHNICAL CONTACT"
    "TECHNICAL CONTACT"})

(defn- contact-elem->personnel
  [contact-elem type]
  (let [emails (map #(c/map->Contact {:type :email :value %})
                    (cx/strings-at-path contact-elem [:Email]))
        personnel (cond (= type :person)
                        {:last-name (cx/string-at-path contact-elem [:Last_Name])
                         :first-name (cx/string-at-path contact-elem [:First_Name])
                         :middle-name (cx/string-at-path contact-elem [:Middle_Name])}
                        (= type :group)
                        {:last-name (cx/string-at-path contact-elem [:Name])})]
    ;; Address is ignored since UMM expects a single value for value field within Contact record
    ;; and address type is not defined in UMM. We also ignore phone as none of the other formats
    ;; read or write phone numbers
    (assoc personnel :contacts emails)))


(defn xml-elem->personnel
  "Returns the personnel records for a parsed Collection XML structure or nil if the elements
  are not available."
  [xml-struct]
  (seq (for [person-elem (cx/elements-at-path xml-struct [:Personnel])
             :let [contact-person-elem (cx/element-at-path person-elem [:Contact_Person])
                   contact-group-elem (cx/element-at-path person-elem [:Contact_Group])
                   personnel (if contact-person-elem
                               (contact-elem->personnel contact-person-elem :person)
                               (contact-elem->personnel contact-group-elem :group))
                   roles (cx/strings-at-path person-elem [:Role])
                   personnel (assoc personnel :roles roles)]]
         (c/map->Personnel personnel))))


(defn- generate-emails
  "Generte the XML entries for the emails associated with a person."
  [contacts]
  (for [contact contacts
        :when (= :email (:type contact))]
    (x/element :Email {} (:value contact))))


(defn- generate-roles
  "Generates the XML entries for the roles associated with a person."
  [roles]
  (or (seq (for [role roles
                 :when (get personnel-roles role)]
             (x/element :Role {} role)))
      ;; If non of the roles match with one of the enumeraion types suppored by DIF10,
      ;; a role of "TECHNICAL CONTACT" is given since role is required field in DIF10.
      [(x/element :Role {} "TECHNICAL CONTACT")]))

(defn generate-personnel
  "Generates the XML entries for a collection's personnel field."
  [personnel]
  (for [{:keys [first-name middle-name last-name contacts roles]} personnel]
    (x/element :Personnel {}
               (generate-roles roles)
               (x/element :Contact_Person {}
                          (gu/optional-elem :First_Name first-name)
                          (gu/optional-elem :Middle_Name middle-name)
                          (x/element :Last_Name {} last-name)
                          (generate-emails contacts)))))
