(ns cmr.umm.dif.collection.personnel
  "Provides functions to parse and generate DIF Personnel elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

(defn xml-elem->personnel
  "Returns the personnel records for a parsed Collection XML structure or nil if the elements
  are not available."
  [xml-struct]
  (seq (flatten
         (for [person-elem (cx/elements-at-path xml-struct [:Personnel])
               :let [last-name (cx/string-at-path person-elem [:Last_Name])
                     first-name (cx/string-at-path person-elem [:First_Name])
                     emails (cx/strings-at-path person-elem [:Email])
                     contacts (map #(c/map->Contact {:type :email
                                                     :value %})
                                   emails)
                     roles (cx/strings-at-path person-elem [:Role])]]
           (c/map->Personnel {:first-name first-name
                              :last-name last-name
                              :contacts contacts
                              :roles roles})))))

(defn- generate-emails
  "Generte the XML entriesfor the emails associated with a person."
  [contacts]
  (for [contact contacts
        :when (= :email (:type contact))]
    (x/element :Email {} (:value contact))))

(defn- generate-roles
  "Generates the XML entries for the roles associated with a person."
  [roles]
  (for [role roles]
    (x/element :Role {} role)))

(defn generate-personnel
  "Generates the XML entries for a collection's personnel field."
  [personnel]
  (for [{:keys [first-name last-name contacts roles]} personnel]
    (x/element :Personnel {}
               (generate-roles roles)
               (when first-name
                 (x/element :First_Name {} first-name))
               (when last-name
                 (x/element :Last_Name {} last-name))
               (generate-emails contacts))))
