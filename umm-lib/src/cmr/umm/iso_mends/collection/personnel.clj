(ns cmr.umm.iso-mends.collection.personnel
  "Contains functions for parsing and generating the ISO MENDS personnel"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]
            [cmr.umm.iso-mends.collection.instrument :as inst]
            [cmr.umm.iso-mends.collection.keyword :as k]
            [cmr.umm.iso-mends.collection.helper :as h]
            [cmr.umm.generator-util :as gu]))

(defn- xml-elem->contact-name
  "Returns the contact name from a parsed IdentificationInfo XML structure"
  [xml-struct]
  (let [person-name (cx/string-at-path xml-struct [:pointOfContact
                                                   :CI_ResponsibleParty
                                                   :individualName
                                                   :CharacterString])
        org-name (cx/string-at-path xml-struct [:pointOfContact
                                                :CI_ResponsibleParty
                                                :organisationName
                                                :CharacterString])]
    (or person-name org-name)))


(defn- xml-elem->email
  "Returns the contact email from a parsed IdentificationInfo XML structure"
  [xml-struct]
  (cx/string-at-path xml-struct [:pointOfContact
                                 :CI_ResponsibleParty
                                 :contactInfo
                                 :CI_Contact
                                 :address
                                 :CI_Address
                                 :electronicMailAddress
                                 :CharacterString]))

(defn- xml-elem->role
  "Returns the personnel role from a parsed XML structure"
  [xml-struct]
  (cx/string-at-path xml-struct [:pontOfContact
                                 :CI_ResponsibleParty
                                 :role
                                 :CI_RoleCode
                                 :codeListValue]))

(defn xml-elem->personnel
  "Returns the personnel from a parsed XML structure"
  [xml-struct]
  (let [email (xml-elem->email xml-struct)
        contact-name (xml-elem->contact-name xml-struct)
        contact (c/map->Contact {:type :email
                                 :value email})
        role (xml-elem->role xml-struct)]
    [(c/map->Personnel {:roles [role]
                        :contacts [contact]
                        :last-name contact-name})]))
