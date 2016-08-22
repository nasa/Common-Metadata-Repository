(ns cmr.umm.echo10.collection.personnel
  "Provides functions to parse and generate ECHO10 personnel related elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

(def DEFAULT_FIRST_NAME
  "ECHO10 requires a FirstName tag, but the UMM does not. This is the default if it is
  not available."
  "unknown")

(defn ignore-default-first-name
  "Converts default first names to nil. This is necessary to fix broken format converison
  ISO tests."
  [first-name]
  (when-not (= DEFAULT_FIRST_NAME first-name)
    first-name))

(defn xml-elem->personnel
  "Returns the personnel records for a parsed Collection XML structure or nil if the elements
  are not available."
  [xml-struct]
  (seq (flatten
         (for [contact (cx/elements-at-path xml-struct [:Contacts :Contact])
               :let [contact-person (cx/element-at-path contact [:ContactPersons :ContactPerson])
                     first-name (cx/string-at-path contact-person [:FirstName])
                     middle-name (cx/string-at-path contact-person [:MiddleName])
                     last-name (cx/string-at-path contact-person [:LastName])
                     emails (cx/strings-at-path contact [:OrganizationEmails :Email])
                     email-contacts (map #(c/->Contact :email %) emails)
                     role (cx/string-at-path contact [:Role])]]
           (c/map->Personnel {:first-name (ignore-default-first-name first-name)
                              :middle-name middle-name
                              :last-name last-name
                              :contacts email-contacts
                              :roles [role]})))))

(defn- generate-contact-person
  "Generates the XML entry for a ContactPerson."
  [first-name middle-name last-name]
  (let [first-name (or first-name DEFAULT_FIRST_NAME)]
    (x/element :ContactPersons {}
               (x/element :ContactPerson {}
                          (x/element :FirstName {}
                                     (util/trunc first-name 255))
                          (when middle-name
                            (x/element :MiddleName {}
                                       (util/trunc middle-name 255)))
                          (x/element :LastName {}
                                     (util/trunc last-name 255))))))

(defn generate-personnel
  "Generates the XML entries for a collection's personnel field."
  [personnel]
  (when (not-empty personnel)
    (x/element :Contacts {}
               (for [{:keys [first-name middle-name last-name contacts roles]} personnel
                     :let [emails (filter #(= :email (:type %)) contacts)]]
                 (x/element :Contact {}
                            (when-let [role (first roles)]
                              (x/element :Role {} (util/trunc role 80)))
                            (when (not-empty emails)
                              (x/element :OrganizationEmails {}
                                         (for [email emails]
                                           (x/element :Email {}
                                                      (util/trunc (:value email) 1024)))))
                            (generate-contact-person first-name middle-name last-name))))))
